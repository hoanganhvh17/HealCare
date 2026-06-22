package com.bookinghealthy.controller.api;

import com.bookinghealthy.model.Post;
import com.bookinghealthy.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/news")
public class NewsApiController {

    @Autowired
    private PostService postService;

    @GetMapping("/latest-alert")
    public ResponseEntity<?> getLatestAlert() {
        Optional<Post> latestPost = postService.findLatestEmergencyAlert();
        if (latestPost.isPresent()) {
            Post post = latestPost.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", post.getId());
            response.put("title", post.getTitle());
            response.put("summary", post.getSummary());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.noContent().build();
        }
    }
}
