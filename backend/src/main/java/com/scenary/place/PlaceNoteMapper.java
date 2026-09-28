package com.scenary.place;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlaceNoteMapper {

    List<PlaceNoteRow> selectNotesInBbox(@Param("minLat") double minLat,
                                         @Param("maxLat") double maxLat,
                                         @Param("minLng") double minLng,
                                         @Param("maxLng") double maxLng,
                                         @Param("cursorCreatedAt") Date cursorCreatedAt,
                                         @Param("cursorId") Long cursorId,
                                         @Param("viewerId") Long viewerId,
                                         @Param("limit") int limit);
}
