package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.invite.InviteInspectResponse;
import com.bni.orange.wallet.service.invite.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets/invites")
public class InviteController {

  private final InviteService invite;
  @GetMapping("/inspect") // PUBLIC
  public ResponseEntity<ApiResponse<InviteInspectResponse>> inspect(@RequestParam("token") String token) {
    var dto = invite.inspect(token);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

}
