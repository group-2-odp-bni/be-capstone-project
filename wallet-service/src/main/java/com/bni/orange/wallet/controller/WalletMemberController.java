package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.request.invite.GeneratedInvite;
import com.bni.orange.wallet.model.request.invite.VerifyInviteCodeRequest;
import com.bni.orange.wallet.model.request.member.WalletMemberInviteRequest;
import com.bni.orange.wallet.model.request.member.WalletMemberUpdateRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.invite.VerifyInviteCodeResponse;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.model.response.member.MyRoleResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberDetailResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberListItemResponse;
import com.bni.orange.wallet.service.command.MembershipCommandService;
import com.bni.orange.wallet.service.invite.InviteService;
import com.bni.orange.wallet.service.query.MembershipQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}")
@Validated
public class WalletMemberController {

  private final MembershipCommandService command;
  private final MembershipQueryService query;
  private final InviteService inviteService;

  public WalletMemberController(MembershipCommandService command,
                                MembershipQueryService query,
                                InviteService inviteService) {
    this.command = command; this.query = query; this.inviteService = inviteService;
  }

  @PostMapping("/members")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletMemberDetailResponse>> inviteExistingUser(
      @PathVariable UUID walletId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
      @Valid @RequestBody WalletMemberInviteRequest req
  ) {
    var dto = command.inviteMember(walletId, req, idemKey);
    return ResponseEntity
        .created(URI.create(String.format("/api/v1/wallets/%s/members/%s", walletId, dto.getUserId())))
        .body(ApiResponse.ok("Member invited", dto));
  }

  @PostMapping("/invites")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<GeneratedInvite>> generateInviteLinkByPhone(
      @PathVariable UUID walletId,
      @RequestParam("phoneE164") String phoneE164,
      @RequestParam(name = "role", defaultValue = "VIEWER") WalletMemberRole role
  ) {
    var normalized = normalizePhone(phoneE164);
    validateE164(normalized);
    var dto = inviteService.generateInviteLink(walletId, null, normalized, role);
    return ResponseEntity.ok(ApiResponse.ok("Invite link generated", dto));
  }
  @PostMapping("/invites/verify")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<VerifyInviteCodeResponse>> verifyInviteCode(
      @PathVariable UUID walletId,
      @Valid @RequestBody VerifyInviteCodeRequest req
  ) {
    var res = inviteService.verifyCode(req.getToken(), req.getCode());
    return ResponseEntity.ok(ApiResponse.ok("Invite code verified", res));
  }

  @GetMapping("/members")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<List<WalletMemberListItemResponse>>> listMembers(
      @PathVariable UUID walletId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(name = "includePending", defaultValue = "false") boolean includePending
  ) {
      var list = query.listMembers(walletId, page, size, includePending);
      return ResponseEntity.ok(ApiResponse.ok("OK", list));
  }
  @PatchMapping("/members/{userId}")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletMemberDetailResponse>> updateMember(
      @PathVariable UUID walletId,
      @PathVariable UUID userId,
      @Valid @RequestBody WalletMemberUpdateRequest req
  ) {
    var dto = command.updateMember(walletId, userId, req);
    return ResponseEntity.ok(ApiResponse.ok("Member updated", dto));
  }

  @DeleteMapping("/members/{userId}")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<MemberActionResultResponse>> removeMember(
      @PathVariable UUID walletId,
      @PathVariable UUID userId
  ) {
    var result = command.removeMember(walletId, userId);
    return ResponseEntity.ok(ApiResponse.ok("Member removed", result));
  }


  @PostMapping("/members/me/leave")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<MemberActionResultResponse>> leaveWallet(
      @PathVariable UUID walletId
  ) {
    var result = command.leaveWallet(walletId);
    return ResponseEntity.ok(ApiResponse.ok("Left wallet", result));
  }

  @GetMapping("/me/role")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<MyRoleResponse>> getMyRole(
      @PathVariable UUID walletId
  ) {
    var dto = query.getMyRole(walletId);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

  private static String normalizePhone(String p) {
    if (p == null) return null;
    var s = p.trim().replaceAll("\\s+", "");
    if (s.startsWith("0")) s = "+62" + s.substring(1);
    return s;
  }
  private static void validateE164(String p) {
    if (p == null || !p.matches("^\\+[1-9]\\d{7,14}$"))
      throw new com.bni.orange.wallet.exception.business.ValidationFailedException("Invalid E.164 phone number");
  }
}
