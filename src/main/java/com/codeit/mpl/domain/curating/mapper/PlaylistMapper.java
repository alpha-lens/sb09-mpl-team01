package com.codeit.mpl.domain.curating.mapper;

import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.entity.Playlist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlaylistMapper {

  @Mapping(source = "owner.id", target = "ownerId")
  @Mapping(source = "owner.name", target = "ownerName")
  PlaylistDto toDto(Playlist playlist);
}