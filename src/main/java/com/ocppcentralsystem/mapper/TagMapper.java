package com.ocppcentralsystem.mapper;

import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagDTO;
import com.ocppcentralsystem.model.TagStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TagMapper {

    @Mapping(target = "expired", expression = "java(tag.isExpired())")
    @Mapping(target = "usable", expression = "java(tag.isUsable())")
    @Mapping(target = "effectiveStatus", expression = "java(resolveEffectiveStatus(tag))")
    TagDTO toDto(Tag tag);

    List<TagDTO> toDtoList(List<Tag> tags);

    /**
     * Collapses the stored status and the expiry date into the single state a caller
     * (or a UI) actually cares about.
     */
    default String resolveEffectiveStatus(Tag tag) {
        if (tag == null) {
            return null;
        }
        if (TagStatus.BLOCKED.equals(tag.getStatus())) {
            return TagStatus.BLOCKED.name();
        }
        return tag.isExpired() ? "EXPIRED" : TagStatus.ACTIVE.name();
    }
}
