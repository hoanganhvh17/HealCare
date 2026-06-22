package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.JobPosting;
import com.bookinghealthy.repository.JobPostingRepository;
import com.bookinghealthy.service.JobPostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobPostingServiceImpl implements JobPostingService {

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Override
    public List<JobPosting> findAll() {
        return jobPostingRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<JobPosting> findActiveJobs() {
        return jobPostingRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Override
    public Optional<JobPosting> findById(Long id) {
        return jobPostingRepository.findById(id);
    }

    @Override
    public JobPosting save(JobPosting jobPosting) {
        return jobPostingRepository.save(jobPosting);
    }

    @Override
    public void deleteById(Long id) {
        jobPostingRepository.deleteById(id);
    }
}