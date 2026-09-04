package com.scenary.media;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VideoUploadSessionMapper {

    int insertSession(VideoUploadSessionEntity session);

    int insertParts(@Param("parts") List<VideoUploadPartEntity> parts);

    VideoUploadSessionEntity findById(@Param("uploadId") String uploadId);

    List<VideoUploadPartEntity> findParts(@Param("uploadId") String uploadId);

    int updatePartObservation(@Param("uploadId") String uploadId,
                              @Param("partNumber") int partNumber,
                              @Param("sizeBytes") long sizeBytes);

    int claimMerging(@Param("uploadId") String uploadId, @Param("now") Date now);

    int releaseMerging(@Param("uploadId") String uploadId);

    int markCompleted(@Param("uploadId") String uploadId, @Param("mediaId") long mediaId);

    int markExpired(@Param("uploadId") String uploadId, @Param("now") Date now);

    List<VideoUploadSessionEntity> selectExpired(@Param("now") Date now,
                                                  @Param("limit") int limit);

    int deleteSession(@Param("uploadId") String uploadId);
}
