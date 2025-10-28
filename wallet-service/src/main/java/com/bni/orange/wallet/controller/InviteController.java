package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.invite.InviteInspectResponse;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.service.invite.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invites")
public class InviteController {

  private final InviteService invite;

  @GetMapping("/inspect") // PUBLIC
  public ResponseEntity<ApiResponse<InviteInspectResponse>> inspect(@RequestParam("token") String token) {
    var dto = invite.inspect(token);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

  @PostMapping("/accept") // AUTH
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<MemberActionResultResponse>> accept(@RequestParam("token") String token) {
    var res = invite.acceptToken(token);
    return ResponseEntity.ok(ApiResponse.ok("Invite accepted", res));
  }
}
