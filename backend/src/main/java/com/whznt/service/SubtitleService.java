package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.SubtitleRequests;
import com.whznt.entity.SubtitleTask;
import com.whznt.vo.SubtitleViews;
import org.springframework.core.io.Resource;

import java.util.List;

public interface SubtitleService extends IService<SubtitleTask> {

    List<SubtitleViews.Summary> tasks(long ownerId);

    SubtitleViews.Detail create(long ownerId, SubtitleRequests.Create request);

    SubtitleViews.Detail read(long ownerId, long id);

    SubtitleViews.Detail retry(long ownerId, long id);

    SubtitleViews.Detail edit(long ownerId, long id, SubtitleRequests.Edit request);

    SubtitleViews.Detail exportVideo(long ownerId, long id, int version);

    Resource video(long ownerId, long id);

    SrtDownload downloadSrt(long ownerId, long id, int version);

    VideoDownload downloadVideo(long ownerId, long id, int version);

    void recover();

    SubtitleTask claimNext();

    void perform(SubtitleTask task);

    void fail(SubtitleTask task, String message);

    record SrtDownload(String filename, String content) {
    }

    record VideoDownload(String filename, Resource resource) {
    }
}
