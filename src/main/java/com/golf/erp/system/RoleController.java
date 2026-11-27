package com.golf.erp.system;

import com.golf.erp.system.SystemDtos.PermissionCodesRequest;
import com.golf.erp.system.SystemDtos.PermissionResponse;
import com.golf.erp.system.SystemDtos.RoleCreateRequest;
import com.golf.erp.system.SystemDtos.RoleResponse;
import com.golf.erp.system.SystemDtos.RoleUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "시스템 - 역할·권한")
@RestController
@RequestMapping("/api")
public class RoleController {
	private final RoleService service;

	public RoleController(RoleService service) {
		this.service = service;
	}

	@GetMapping("/roles")
	public List<RoleResponse> list() {
		return service.list();
	}

	@GetMapping("/roles/{id}")
	public RoleResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping("/roles")
	@ResponseStatus(HttpStatus.CREATED)
	public RoleResponse create(@Valid @RequestBody RoleCreateRequest r) {
		return service.create(r);
	}

	@PutMapping("/roles/{id}")
	public RoleResponse update(@PathVariable long id, @Valid @RequestBody RoleUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "역할 권한 지정(전체 교체)", description = "permissionCodes 예: [\"MEMBER_READ\", \"RESERVATION_WRITE\"]")
	@PutMapping("/roles/{id}/permissions")
	public RoleResponse replacePermissions(@PathVariable long id, @Valid @RequestBody PermissionCodesRequest r) {
		return service.replacePermissions(id, r.permissionCodes());
	}

	@DeleteMapping("/roles/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}

	@Operation(summary = "권한 목록", description = "모듈마다 _READ(조회), _WRITE(등록·수정·처리) 두 가지")
	@GetMapping("/permissions")
	public List<PermissionResponse> permissions() {
		return service.permissions();
	}
}
