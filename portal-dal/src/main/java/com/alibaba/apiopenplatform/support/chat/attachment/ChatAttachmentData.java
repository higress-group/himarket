package com.alibaba.apiopenplatform.support.chat.attachment;

import lombok.Data;

/**
 * @author zh
 */
@Data
public class ChatAttachmentData {

//    private AttachmentDataType type;

    private byte[] data;

    public boolean isBlob() {
        return data != null && data.length > 0;
    }
}
