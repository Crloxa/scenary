package com.scenary.search;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchMapper {

    List<SearchRow> selectNotes(@Param("keyword") String keyword,
                                @Param("sort") String sort,
                                @Param("cursorScore") Integer cursorScore,
                                @Param("cursorCreatedAt") Date cursorCreatedAt,
                                @Param("cursorId") Long cursorId,
                                @Param("limit") int limit);
}
