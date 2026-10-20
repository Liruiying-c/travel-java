package org.example.traveljava.service;

import org.example.traveljava.Mapper.HistoryMapper;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.example.traveljava.vo.TravelSearchVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 行程历史/收藏的业务逻辑。重点验证两件事：
 * 1) 分支选对了（收藏列表 vs 全部列表）；
 * 2) 写操作顺序对了（先查 tripId 再删两张表，先插 trip 再插 search）。
 */
class HistoryServiceTest {

    @Mock
    private HistoryMapper historyMapper;

    @InjectMocks
    private HistoryService historyService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("collected=1 走收藏列表")
    void historyList_shouldUseCollectQuery() {
        when(historyMapper.selectCollect(1)).thenReturn(List.of(search(1)));

        Result<List> result = historyService.historyList(1, 1);

        assertTrue(result.getSuccess());
        assertEquals(1, result.getData().size());
        verify(historyMapper).selectCollect(1);
        verify(historyMapper, never()).selectALL(any());
    }

    @Test
    @DisplayName("collected=0 走全部列表")
    void historyList_shouldUseAllQuery() {
        when(historyMapper.selectALL(1)).thenReturn(List.of(search(1), search(2)));

        Result<List> result = historyService.historyList(1, 0);

        assertTrue(result.getSuccess());
        assertEquals(2, result.getData().size());
        verify(historyMapper).selectALL(1);
        verify(historyMapper, never()).selectCollect(any());
    }

    @Test
    @DisplayName("查询失败：返回 error 结构，保留原始错误信息")
    void historyList_shouldReturnError() {
        when(historyMapper.selectALL(1)).thenThrow(new RuntimeException("db down"));

        Result<List> result = historyService.historyList(1, 0);

        assertFalse(result.getSuccess());
        assertEquals("error", result.getError());
        assertEquals("db down", result.getRawResponse());
    }

    @Test
    @DisplayName("查详情：先用 search.id 查 trip_id，再查行程")
    void selectRecommend_shouldResolveTripId() {
        TravelRecommendVO trip = new TravelRecommendVO();
        trip.setCity("北京");
        when(historyMapper.selectTripId(9)).thenReturn(5);
        when(historyMapper.selectTripById(5)).thenReturn(trip);

        Result<TravelRecommendVO> result = historyService.selectRecommend(9);

        assertTrue(result.getSuccess());
        assertEquals("北京", result.getData().getCity());
        verify(historyMapper).selectTripId(9);
        verify(historyMapper).selectTripById(5);
    }

    @Test
    @DisplayName("保存行程：先插 t_trip，再插 t_search")
    void saveTrip_shouldInsertTripThenSearch() {
        TravelRecommendVO trip = new TravelRecommendVO();
        trip.setCity("北京");

        Result result = historyService.saveTrip(trip);

        assertTrue(result.getSuccess());
        InOrder inOrder = inOrder(historyMapper);
        inOrder.verify(historyMapper).insertTrip(trip);
        inOrder.verify(historyMapper).insertSearch(trip);
    }

    @Test
    @DisplayName("保存行程失败：返回错误结构")
    void saveTrip_shouldReturnError() {
        TravelRecommendVO trip = new TravelRecommendVO();
        org.mockito.Mockito.doThrow(new RuntimeException("insert failed"))
                .when(historyMapper).insertTrip(trip);

        Result result = historyService.saveTrip(trip);

        assertFalse(result.getSuccess());
        assertEquals("insert failed", result.getRawResponse());
    }

    @Test
    @DisplayName("收藏/取消收藏")
    void updateCollect_shouldCallMapper() {
        Result result = historyService.updateCollect(9, 1);

        assertTrue(result.getSuccess());
        assertEquals("操作成功", result.getData());
        verify(historyMapper).updateCollect(9, 1);
    }

    @Test
    @DisplayName("查询是否收藏")
    void checkCollect_shouldReturnFlag() {
        when(historyMapper.checkCollect(9)).thenReturn(1);

        Result result = historyService.checkCollect(9);

        assertTrue(result.getSuccess());
        assertEquals(1, result.getData());
    }

    @Test
    @DisplayName("删除行程：先查 tripId，再删 search 和 trip")
    void deleteTrip_shouldDeleteBothTables() {
        when(historyMapper.selectTripId(9)).thenReturn(5);

        Result result = historyService.deleteTrip(9);

        assertTrue(result.getSuccess());
        InOrder inOrder = inOrder(historyMapper);
        inOrder.verify(historyMapper).selectTripId(9);
        inOrder.verify(historyMapper).deleteSearch(9);
        inOrder.verify(historyMapper).deleteTrip(5);
    }

    @Test
    @DisplayName("删除失败：返回错误结构")
    void deleteTrip_shouldReturnError() {
        when(historyMapper.selectTripId(9)).thenReturn(5);
        org.mockito.Mockito.doThrow(new RuntimeException("delete failed"))
                .when(historyMapper).deleteSearch(9);

        Result result = historyService.deleteTrip(9);

        assertFalse(result.getSuccess());
        assertEquals("delete failed", result.getRawResponse());
    }

    private TravelSearchVO search(int id) {
        TravelSearchVO vo = new TravelSearchVO();
        vo.setId(id);
        vo.setUserId(1);
        vo.setTripId(id);
        vo.setCity("北京");
        vo.setDays(3);
        vo.setTotalBudget(5000.0);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }
}
