package Protocol;

import reso.common.Message;

public class Ack implements Message {
    public int sequenceNumber;
    @Override
    public int getByteLength() {
        return 0;
    }
}
