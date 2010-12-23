package org.mobicents.ss7.management.transceiver;

import java.nio.ByteBuffer;

public class MessageFactory {

    private byte[] header = new byte[4];
    private boolean isHeaderReady = false;

    private int pos = 0;
    private int length;

    private byte[] params;

    private Message message;

    public Message createMessage(String message) {
        return new Message(message.getBytes());
    }

    public Message createMessage(ByteBuffer buffer) {
        if (!isHeaderReady) {
            int len = Math.min(header.length - pos, buffer.remaining());
            buffer.get(header, pos, len);

            // update cursor postion in the header's buffer
            pos += len;

            // header completed?
            isHeaderReady = pos == header.length;

            if (!isHeaderReady) {
                // no more data available
                return null;
            }

            // obtain remaining length of the message and prepare buffer
            length = ((header[0] & 0xff << 24) | (header[1] & 0xff << 16)
                    | (header[2] & 0xff << 8) | (header[3] & 0xff)) - 4;

            params = new byte[length];

            // finally switch cursor position
            pos = 0;

            message = new Message();
        }

        // at this point we must recheck remainder of the input buffer
        // because possible case when input buffer fits exactly to the header
        if (!buffer.hasRemaining()) {
            return null;
        }

        // again, reading all parameters before parsing

        // compute available or required data
        int len = Math.min(params.length, buffer.remaining());
        buffer.get(params, pos, len);

        // update cursor position
        pos += len;

        // end of message not reached
        if (pos < params.length) {
            return null;
        }

        // end of message reached and most probably some data remains in buffer
        // do not touch remainder of the input buffer, next call to this method
        // will proceed remainder

        // parsing params of this message
        message.decode(params);

        // switch factory for receiving new message
        this.isHeaderReady = false;
        this.pos = 0;

        // return
        return message;
    }

}