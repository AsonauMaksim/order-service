package com.internship.orderservice.mapper;

import com.internship.orderservice.dto.external.UserResponse;
import com.internship.orderservice.dto.response.UserInfoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserInfoMapper {

    UserInfoResponse toDto(UserResponse userResponse);
}
