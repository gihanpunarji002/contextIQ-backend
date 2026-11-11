package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {
    private final FolderService service;
    public FolderController(FolderService service) { this.service = service; }
    @GetMapping public List<FolderView> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FolderView create(@Valid @RequestBody FolderRequest request) { return service.save(null, request); }
    @PostMapping("/{id}")
    public FolderView update(@PathVariable UUID id, @Valid @RequestBody FolderRequest request) { return service.save(id, request); }
    @PostMapping("/move") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(@Valid @RequestBody MoveRequest request) { service.move(request); }
}
