package org.example.traveljava.Mapper;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.traveljava.vo.TravelChatVO;
import org.example.traveljava.vo.TravelRecommendVO;
import org.example.traveljava.vo.TravelSearchVO;
import org.example.traveljava.vo.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真库集成测试：Testcontainers 起一个真 MySQL，跑真 SQL。
 * 不用 H2，因为 H2 不支持 MySQL 的 JSON 列和方言。
 * 本机没有 Docker 时整类自动跳过，不影响其他测试。
 */
@Testcontainers(disabledWithoutDocker = true)
class MapperIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("travel_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema-test.sql");

    private static SqlSessionFactory factory;

    @BeforeAll
    static void setUp() throws Exception {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/" + MYSQL.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8");
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());

        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(ChatMapper.class);
        // HistoryMapper 的 XML 在小写 mapper 目录，与接口包名 Mapper 大小写不一致，
        // 这里按真实路径显式加载，避免依赖 Windows 文件系统大小写不敏感
        try (InputStream in = Resources.getResourceAsStream("org/example/traveljava/mapper/HistoryMapper.xml")) {
            assertNotNull(in, "classpath 下找不到 HistoryMapper.xml");
            new XMLMapperBuilder(in, configuration, "HistoryMapper.xml", configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    @DisplayName("UserMapper：新增、查询、改昵称、改密码")
    void userMapper_shouldWork() {
        try (SqlSession session = factory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            UserVO user = new UserVO();
            user.setUsername("ut_user_1");
            user.setPasswordHash("HASH_1");
            user.setNickname("小明");
            mapper.insert(user);

            UserVO loaded = mapper.SelectByUsername("ut_user_1");
            assertNotNull(loaded);
            assertEquals("ut_user_1", loaded.getUsername());
            assertEquals("小明", loaded.getNickname());

            mapper.updateNickname(loaded.getId(), "新昵称");
            assertEquals("新昵称", mapper.SelectByUsername("ut_user_1").getNickname());

            assertEquals("HASH_1", mapper.selectPassword(loaded.getId()));
            mapper.updatePassword(loaded.getId(), "HASH_2");
            assertEquals("HASH_2", mapper.selectPassword(loaded.getId()));
        }
    }

    @Test
    @DisplayName("HistoryMapper：行程 JSON 落库、列表、收藏、详情、删除")
    void historyMapper_shouldWork() {
        try (SqlSession session = factory.openSession(true)) {
            int userId = createUser(session, "ut_user_2");
            HistoryMapper mapper = session.getMapper(HistoryMapper.class);

            TravelRecommendVO trip = new TravelRecommendVO();
            trip.setUserId(userId);
            trip.setCity("北京");
            trip.setDays(3);
            trip.setTotalBudget(5000.0);
            trip.setDailyItinerary(List.of(day("故宫")));
            TravelRecommendVO.BudgetBreakdown breakdown = new TravelRecommendVO.BudgetBreakdown();
            breakdown.setAccommodation(1500.0);
            breakdown.setFood(1200.0);
            trip.setBudgetBreakdown(breakdown);
            trip.setTips(List.of("提前订票"));
            trip.setWarnings(List.of("注意保暖"));

            mapper.insertTrip(trip);
            assertNotNull(trip.getTripId(), "insertTrip 应该回填 tripId");
            mapper.insertSearch(trip);

            List<TravelSearchVO> all = mapper.selectALL(userId);
            assertEquals(1, all.size());
            assertEquals("北京", all.get(0).getCity());
            assertEquals(3, all.get(0).getDays());
            assertEquals(5000.0, all.get(0).getTotalBudget());

            TravelRecommendVO detail = mapper.selectTripById(trip.getTripId());
            assertEquals("北京", detail.getCity());
            assertNotNull(detail.getDailyItinerary());
            assertEquals(1, detail.getDailyItinerary().size());
            assertNotNull(detail.getTips());

            Integer searchId = all.get(0).getId();
            mapper.updateCollect(searchId, 1);
            assertEquals(1, mapper.checkCollect(searchId));
            assertEquals(1, mapper.selectCollect(userId).size());

            mapper.deleteSearch(searchId);
            mapper.deleteTrip(trip.getTripId());
            assertTrue(mapper.selectALL(userId).isEmpty());
            assertNull(mapper.selectTripById(trip.getTripId()));
        }
    }

    @Test
    @DisplayName("ChatMapper：按用户查询聊天记录")
    void chatMapper_shouldWork() {
        try (SqlSession session = factory.openSession(true)) {
            int userId = createUser(session, "ut_user_3");
            ChatMapper mapper = session.getMapper(ChatMapper.class);

            TravelChatVO chat = new TravelChatVO();
            chat.setUserId(userId);
            chat.setUserMsg("北京有什么好吃的");
            chat.setAiMsg("烤鸭、涮肉");
            chat.setUserTime(LocalDateTime.now().withNano(0));
            chat.setAiTime(LocalDateTime.now().withNano(0));
            mapper.insert(chat);

            List<TravelChatVO> history = mapper.selectByUserId(String.valueOf(userId));
            assertEquals(1, history.size());
            assertEquals("北京有什么好吃的", history.get(0).getUserMsg());
            assertEquals("烤鸭、涮肉", history.get(0).getAiMsg());
        }
    }

    private int createUser(SqlSession session, String username) {
        UserMapper mapper = session.getMapper(UserMapper.class);
        UserVO user = new UserVO();
        user.setUsername(username);
        user.setPasswordHash("HASH");
        user.setNickname("测试用户");
        mapper.insert(user);
        return mapper.SelectByUsername(username).getId();
    }

    private TravelRecommendVO.DailyItinerary day(String spot) {
        TravelRecommendVO.Timeslot morning = new TravelRecommendVO.Timeslot();
        morning.setSpot(spot);
        morning.setDuration("3小时");
        morning.setTicket("60元");
        morning.setTransportation("地铁");
        morning.setDescription("必看");

        TravelRecommendVO.DailyItinerary day = new TravelRecommendVO.DailyItinerary();
        day.setDay(1);
        day.setDate("第1天");
        day.setMorning(morning);
        return day;
    }
}
