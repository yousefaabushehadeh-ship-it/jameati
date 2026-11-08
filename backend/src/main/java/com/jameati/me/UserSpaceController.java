package com.jameati.me;

import static com.jameati.me.UserSpaceDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserSpaceController {
  private final CurrentUserService currentUsers;
  private final UserSpaceService service;

  public UserSpaceController(CurrentUserService currentUsers, UserSpaceService service) {
    this.currentUsers = currentUsers;
    this.service = service;
  }

  @GetMapping("/profile")
  ApiResponse<ProfileResponse> profile(Principal principal) {
    return ApiResponse.ok(service.profile(currentUsers.require(principal)));
  }

  @PutMapping("/profile")
  ApiResponse<ProfileResponse> updateProfile(
      Principal principal, @Valid @RequestBody ProfileUpdate request) {
    return ApiResponse.ok(service.updateProfile(currentUsers.require(principal), request));
  }

  @GetMapping("/applications")
  ApiResponse<Page<ApplicationResponse>> applications(
      Principal principal,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.applications(currentUsers.require(principal), pageable));
  }

  @PostMapping("/applications")
  ResponseEntity<ApiResponse<ApplicationResponse>> createApplication(
      Principal principal, @Valid @RequestBody ApplicationCreate request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.created(
                service.createApplication(currentUsers.require(principal), request)));
  }

  @PutMapping("/applications/{id}")
  ApiResponse<ApplicationResponse> updateApplication(
      Principal principal,
      @PathVariable Long id,
      @Valid @RequestBody ApplicationUpdate request) {
    return ApiResponse.ok(
        service.updateApplication(currentUsers.require(principal), id, request));
  }

  @DeleteMapping("/applications/{id}")
  ApiResponse<Void> deleteApplication(Principal principal, @PathVariable Long id) {
    service.deleteApplication(currentUsers.require(principal), id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/notifications")
  ApiResponse<NotificationInbox> notifications(
      Principal principal,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.notificationInbox(currentUsers.require(principal), pageable));
  }

  @PatchMapping("/notifications/{id}/read")
  ApiResponse<NotificationResponse> readNotification(
      Principal principal, @PathVariable Long id) {
    return ApiResponse.ok(service.readNotification(currentUsers.require(principal), id));
  }

  @PostMapping("/notifications/read-all")
  ApiResponse<Void> readAllNotifications(Principal principal) {
    service.readAllNotifications(currentUsers.require(principal));
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/notifications/{id}")
  ApiResponse<Void> deleteNotification(Principal principal, @PathVariable Long id) {
    service.deleteNotification(currentUsers.require(principal), id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/followed-organizations")
  ApiResponse<List<FollowedOrganizationResponse>> followedOrganizations(Principal principal) {
    return ApiResponse.ok(service.followedOrganizations(currentUsers.require(principal)));
  }

  @PostMapping("/followed-organizations")
  ResponseEntity<ApiResponse<FollowedOrganizationResponse>> followOrganization(
      Principal principal, @Valid @RequestBody FollowedOrganizationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.created(
                service.followOrganization(currentUsers.require(principal), request)));
  }

  @PostMapping("/followed-organizations/{organizationId}")
  ResponseEntity<ApiResponse<FollowedOrganizationResponse>> followOrganization(
      Principal principal, @PathVariable Long organizationId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.created(
                service.followOrganization(
                    currentUsers.require(principal), organizationId)));
  }

  @DeleteMapping("/followed-organizations/{id}")
  ApiResponse<Void> unfollowOrganization(Principal principal, @PathVariable Long id) {
    service.unfollowOrganization(currentUsers.require(principal), id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/recently-viewed")
  ApiResponse<List<RecentlyViewedResponse>> recentlyViewed(Principal principal) {
    return ApiResponse.ok(service.recentlyViewed(currentUsers.require(principal)));
  }

  @PostMapping("/recently-viewed")
  ApiResponse<RecentlyViewedResponse> recordView(
      Principal principal, @Valid @RequestBody RecentlyViewedRequest request) {
    return ApiResponse.ok(service.recordView(currentUsers.require(principal), request));
  }

  @DeleteMapping("/recently-viewed")
  ApiResponse<Void> clearRecentlyViewed(Principal principal) {
    service.clearRecentlyViewed(currentUsers.require(principal));
    return ApiResponse.ok(null);
  }

  @GetMapping("/cvs")
  ApiResponse<List<CvDocumentResponse>> cvs(Principal principal) {
    return ApiResponse.ok(service.cvs(currentUsers.require(principal)));
  }

  @PostMapping("/cvs")
  ResponseEntity<ApiResponse<CvDocumentResponse>> createCv(
      Principal principal, @Valid @RequestBody CvDocumentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.createCv(currentUsers.require(principal), request)));
  }

  @PutMapping("/cvs/{id}")
  ApiResponse<CvDocumentResponse> updateCv(
      Principal principal,
      @PathVariable Long id,
      @Valid @RequestBody CvDocumentRequest request) {
    return ApiResponse.ok(service.updateCv(currentUsers.require(principal), id, request));
  }

  @DeleteMapping("/cvs/{id}")
  ApiResponse<Void> deleteCv(Principal principal, @PathVariable Long id) {
    service.deleteCv(currentUsers.require(principal), id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/personality-results")
  ApiResponse<List<PersonalityResultResponse>> personalityResults(Principal principal) {
    return ApiResponse.ok(service.personalityResults(currentUsers.require(principal)));
  }

  @PostMapping("/personality-results")
  ResponseEntity<ApiResponse<PersonalityResultResponse>> createPersonalityResult(
      Principal principal, @Valid @RequestBody PersonalityResultRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.created(
                service.createPersonalityResult(currentUsers.require(principal), request)));
  }

  @DeleteMapping("/personality-results/{id}")
  ApiResponse<Void> deletePersonalityResult(Principal principal, @PathVariable Long id) {
    service.deletePersonalityResult(currentUsers.require(principal), id);
    return ApiResponse.ok(null);
  }

}
