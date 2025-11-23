package com.alibaba.apiopenplatform.service.impl;

import com.alibaba.apiopenplatform.repository.ChatAttachmentRepository;
import com.alibaba.apiopenplatform.service.ChatAttachmentService;
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
