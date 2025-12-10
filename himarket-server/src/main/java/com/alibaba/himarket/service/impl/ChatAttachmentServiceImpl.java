package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.repository.ChatAttachmentRepository;
import com.alibaba.himarket.service.ChatAttachmentService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author zh
 */
@Service
@AllArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private final ChatAttachmentRepository chatAttachmentRepository;

}
