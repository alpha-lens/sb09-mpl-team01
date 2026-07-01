package com.codeit.mpl.domain.curating.mapper;

import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlaylistMapper {

  default PlaylistDto toDto(Playlist playlist) {
    UserSummary owner = new UserSummary(
        playlist.getOwner().getId(),
        playlist.getOwner().getName(),
        playlist.getOwner().getProfileImageUrl()
    );

    return new PlaylistDto(
        playlist.getId(),
        owner,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        0L,      // subscriberCount → Service에서 채워야 함
        false,   // subscribedByMe → Service에서 채워야 함
        List.of() // contents → Service에서 채워야 함
    );
  }
}