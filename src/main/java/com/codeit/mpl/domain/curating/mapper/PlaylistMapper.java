package com.codeit.mpl.domain.curating.mapper;

import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import java.util.List;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class PlaylistMapper {

  @Autowired
  protected BinaryContentStorage binaryContentStorage;

  public PlaylistDto toDto(Playlist playlist) {
    String ownerImageUrl = playlist.getOwner().getProfileImageUrl() != null
        ? binaryContentStorage.getUrl(playlist.getOwner().getProfileImageUrl())
        : null;
    UserSummary owner = new UserSummary(
        playlist.getOwner().getId(),
        playlist.getOwner().getName(),
        ownerImageUrl
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