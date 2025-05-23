package com.genersoft.iot.vmp.conf;


import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 录像文件定时删除
 */
@Slf4j
@Component
public class CloudRecordTimer {

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private CloudRecordServiceMapper cloudRecordServiceMapper;

    /**
     * 定时查询待删除的录像文件
     */
//    @Scheduled(fixedRate = 10000) //每五秒执行一次，方便测试
    @Scheduled(cron = "0 0 0 * * ?")   //每天的0点执行
    public void execute(){
        log.info("[录像文件定时清理] 开始清理过期录像文件");
        // 获取配置了assist的流媒体节点
        List<MediaServer> mediaServerItemList =  mediaServerService.getAllOnline();
        if (mediaServerItemList.isEmpty()) {
            return;
        }
        long result = 0;
        for (MediaServer mediaServerItem : mediaServerItemList) {

            Calendar lastCalendar = Calendar.getInstance();
            if (mediaServerItem.getRecordDay() > 0) {
                lastCalendar.setTime(new Date());
                // 获取保存的最后截至日[期，因为每个节点都有一个日期，也就是支持每个节点设置不同的保存日期，
                lastCalendar.add(Calendar.DAY_OF_MONTH, -mediaServerItem.getRecordDay());
                Long lastDate = lastCalendar.getTimeInMillis();

                // 获取到截至日期之前的录像文件列表，文件列表满足未被收藏和保持的。这两个字段目前共能一致，
                // 为我自己业务系统相关的代码，大家使用的时候直接使用收藏（collect）这一个类型即可
                List<CloudRecordItem> cloudRecordItemList = cloudRecordServiceMapper.queryRecordListForDelete(lastDate, mediaServerItem.getId());
                if (cloudRecordItemList.isEmpty()) {
                    continue;
                }
                // TODO 后续可以删除空了的过期日期文件夹
                for (CloudRecordItem cloudRecordItem : cloudRecordItemList) {
                    String date = new File(cloudRecordItem.getFilePath()).getParentFile().getName();
                    try {
                        boolean deleteResult = mediaServerService.deleteRecordDirectory(mediaServerItem, cloudRecordItem.getApp(),
                                cloudRecordItem.getStream(), date, cloudRecordItem.getFileName());
                        if (deleteResult) {
                            log.warn("[录像文件定时清理] 删除磁盘文件成功： {}", cloudRecordItem.getFilePath());
                        }
                    }catch (ControllerException ignored) {}

                }
                result += cloudRecordServiceMapper.deleteList(cloudRecordItemList);
            }
        }
        log.info("[录像文件定时清理] 共清理{}个过期录像文件", result);
    }
}
