package com.example.cinema.blImpl.sales;

import com.example.cinema.bl.sales.TicketService;
import com.example.cinema.blImpl.management.hall.HallServiceForBl;
import com.example.cinema.blImpl.management.schedule.ScheduleServiceForBl;
import com.example.cinema.blImpl.promotion.ActivityServiceForBL;
import com.example.cinema.blImpl.promotion.ActivityServiceImpl;
import com.example.cinema.blImpl.promotion.CouponServiceForBL;
import com.example.cinema.blImpl.promotion.VIPCardServiceForBL;
import com.example.cinema.data.management.MovieMapper;
import com.example.cinema.data.management.ScheduleMapper;
import com.example.cinema.data.promotion.ActivityMapper;
import com.example.cinema.data.promotion.CouponMapper;
import com.example.cinema.data.promotion.VIPCardMapper;
import com.example.cinema.data.sales.TicketMapper;
import com.example.cinema.po.*;
import com.example.cinema.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by liying on 2019/4/16.
 */
@Service
public class TicketServiceImpl implements TicketService ,TicketServiceForBl{

    @Autowired
    TicketMapper ticketMapper;
    @Autowired
    ScheduleServiceForBl scheduleService;
    @Autowired
    HallServiceForBl hallService;

    @Autowired
    CouponServiceForBL couponServiceForBL;
    @Autowired
    CouponMapper couponMapper;

    @Autowired
    ActivityServiceForBL activityServiceForBL;
    @Autowired
    ActivityMapper activityMapper;

    @Autowired
    ScheduleMapper scheduleMapper;

    @Autowired
    MovieMapper movieMapper;

    @Autowired
    VIPCardServiceForBL vipCardServiceForBL;


    @Override
    @Transactional
    public ResponseVO addTicket(TicketForm ticketForm) {
        ResponseVO response;
        List<Ticket> tickets = new ArrayList<>();
        Ticket ticket;
        try {
            for (SeatForm seatForm : ticketForm.getSeats()) {
                ticket = new Ticket();
                ticket.setUserId(ticketForm.getUserId());
                ticket.setScheduleId(ticketForm.getScheduleId());
                ticket.setColumnIndex(seatForm.getColumnIndex());
                ticket.setRowIndex(seatForm.getRowIndex());
                ticket.setState(0);
                tickets.add(ticket);
            }
            ticketMapper.insertTickets(tickets);
        } catch (Exception e) {
            e.printStackTrace();
            response = ResponseVO.buildFailure("锁座失败，原因未知");
            return response;
        }
        response = ResponseVO.buildSuccess(tickets);
        return response;

    }


    @Override
    public ResponseVO getBySchedule(int scheduleId) {
        try {
            List<Ticket> tickets = ticketMapper.selectTicketsBySchedule(scheduleId);
            ScheduleItem schedule = scheduleService.getScheduleItemById(scheduleId);
            Hall hall = hallService.getHallById(schedule.getHallId());
            int[][] seats = new int[hall.getRow()][hall.getColumn()];
            tickets.stream().forEach(ticket -> {
                seats[ticket.getRowIndex()][ticket.getColumnIndex()] = 1;
            });
            ScheduleWithSeatVO scheduleWithSeatVO = new ScheduleWithSeatVO();
            scheduleWithSeatVO.setScheduleItem(schedule);
            scheduleWithSeatVO.setSeats(seats);
            return ResponseVO.buildSuccess(scheduleWithSeatVO);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseVO.buildFailure("失败");
        }
    }

    @Override
    public ResponseVO getTicketByUser(int userId) {
        try {
            List<Ticket> tickets = ticketMapper.selectTicketByUser(userId);
            ResponseVO responseVO = ResponseVO.buildSuccess(tickets);
            responseVO.setMessage("查询成功，该用户拥有以下" + tickets.size() + "张票");
            return responseVO;
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseVO.buildFailure("查询用户已购票失败");
        }
    }

    @Override
    @Transactional
    public ResponseVO completeTicket(List<Integer> ticketId, int couponId, boolean isVIP) {
        ResponseVO response;
        try {
            ScheduleItem scheduleItem = ticketMapper.selectScheduleByTicketId(ticketId.get(0));
            List<Ticket> ticketList = new ArrayList<>();
            for (int i : ticketId) {
                ticketList.add(ticketMapper.selectTicketById(i));
            }
            double totalFare = scheduleItem.getFare() * ticketId.size();
            int userId = ticketList.get(0).getUserId();

            /*检验优惠券*/
            if (couponId !=0 ){
                if (!couponServiceForBL.checkCouponValidated(userId, couponId)){
                    throw new Exception("该用户没有此优惠券");
                }else {
                    Coupon coupon = couponServiceForBL.selectCouponsById(couponId);
                    if (totalFare < coupon.getTargetAmount()){
                        throw new Exception("不满足优惠金额条件");
                    }else {
                        totalFare -= coupon.getDiscountAmount();
                        couponServiceForBL.delCouponUser(userId, couponId);
                    }
                }
            }

            /*判断是否符合活动并赠送优惠券*/
            Timestamp ticketTimeStamp = new Timestamp(new Date().getTime());
            List<Activity> activities = activityServiceForBL.selectActivitiesByMovie(scheduleItem.getMovieId());
            int gainCoupons = 0;
            for (Activity a : activities) {
                if (ticketTimeStamp.compareTo(a.getStartTime()) > 0
                        && ticketTimeStamp.compareTo(a.getEndTime()) < 0) {
                    couponServiceForBL.sendCouponUser(a.getCoupon().getId(), userId);
                    gainCoupons += 1;
                }
            }
            //完成付款
            if(isVIP){
                vipCardServiceForBL.pay(userId, totalFare);
            }
            for (int i : ticketId) {
                ticketMapper.completeTicket(i, totalFare/ticketId.size());
            }
            response = ResponseVO.buildSuccess(gainCoupons);
            response.setMessage("购票成功");
        } catch (Exception e) {
            e.printStackTrace();
            response = ResponseVO.buildFailure("购票失败，原因：" + e.getMessage());
        }
        return response;
    }

    @Override
    public ResponseVO cancelTicket(List<Integer> id) {
        try {
            for (int idOfOne : id) {
                if (ticketMapper.selectTicketById(idOfOne).getState() != 2) {
                    ticketMapper.updateTicketState(idOfOne, 2);
                } else {
                    throw new Exception("票未锁座");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseVO.buildFailure("取消购票失败，原因:" + e.getMessage());
        }
        return ResponseVO.buildSuccess("取消购票成功");
    }

    @Override
    public ResponseVO getAllRefundStrategy() {
        ResponseVO response;
        try {
            response = ResponseVO.buildSuccess(ticketMapper.selectRefundStrategy());
            response.setMessage("获取退票策略成功");
        }catch (Exception e){
            e.printStackTrace();
            response = ResponseVO.buildFailure("获取失败，原因未知");
        }
        return response;
    }

    @Override
    public ResponseVO addRefundStrategy(RefundStrategyFrom refundStrategyFrom) {
        ResponseVO response;
        try {
            RefundStrategy refundStrategy = new RefundStrategy();
            refundStrategy.setHoursBeforeEnd(refundStrategyFrom.getHoursBeforeEnd());
            refundStrategy.setRate(refundStrategyFrom.getRate());
            ticketMapper.insertRefundStrategy(refundStrategy);
            RefundStrategyVO refundStrategyVO = new RefundStrategyVO(refundStrategy);
            response = ResponseVO.buildSuccess(refundStrategyVO);
            response.setMessage("更新退票策略成功");
        }catch (Exception e){
            e.printStackTrace();
            response = ResponseVO.buildFailure("发布失败，该退票策略已存在");
        }
        return response;
    }

    @Override
    public ResponseVO updateRefundStrategy(RefundStrategyFrom refundStrategyFrom) {
        ResponseVO response;
        try {
            RefundStrategy refundStrategy = new RefundStrategy();
            refundStrategy.setId(refundStrategyFrom.getId());
            refundStrategy.setHoursBeforeEnd(refundStrategyFrom.getHoursBeforeEnd());
            refundStrategy.setRate(refundStrategyFrom.getRate());
            ticketMapper.updateRefundStrategy(refundStrategy);
            RefundStrategyVO refundStrategyVO = new RefundStrategyVO(refundStrategy);
            response = ResponseVO.buildSuccess(refundStrategyVO);
            response.setMessage("修改退票策略成功");
        }catch (Exception e){
            e.printStackTrace();
            response = ResponseVO.buildFailure("发布失败，该退票策略已存在");
        }
        return response;
    }

    @Override
    public ResponseVO refundTicket(RefundForm refundForm) {
        return null;
    }

    @Override
    public List<Ticket> getTicketByDate(Date startDate, Date endDate) {
        return ticketMapper.selectTicketByDate(startDate,endDate);
    }
}
