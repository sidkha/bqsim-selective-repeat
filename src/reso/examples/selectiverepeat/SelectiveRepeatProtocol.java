package reso.examples.selectiverepeat;

import reso.common.Message;
import reso.ip.*;

import java.util.ArrayList;
import java.util.Random;

public class SelectiveRepeatProtocol implements IPInterfaceListener {
    public static final int IP_PROTO_SR = Datagram.allocateProtocolNumber("SR");
    private final IPHost host;
    public IPAddress dst;
    private int sendBase = 0;
    private int receiveBase = 0;
    private int nextSeqNum = 0;
    private int cwnd = 2;
    private double RTO = 3;
    private double RTT = -1;
    private double SRTT = -1;
    private double depTime, arrTime;

    private boolean congestionAvoidance=false; // Slow-start

    private ArrayList<SelectiveRepeatMessage> queue = new ArrayList<>(); // Les messages à envoyer
    private ArrayList<SelectiveRepeatMessage> messages = new ArrayList<>();

    private Random rand = new Random();

    // Variables pour la gestion de congestion.
    private int MSS = 5;
    private int ssthresh = 100;

    public SelectiveRepeatProtocol(IPHost host) {
        this.host = host;
    }

    private void refresh() throws Exception {
        for (SelectiveRepeatMessage message : queue) { // while possible, send messages in queue.
            if (nextSeqNum < sendBase + cwnd) {
                if (message.seqNum <= nextSeqNum && !message.sent) {
                    depTime = host.getNetwork().getScheduler().getCurrentTime();
                    message.timer = new Timer(host.getNetwork().getScheduler(), RTO, this, message);
                    message.timer.start();
                    host.getIPLayer().send(IPAddress.ANY, dst, SelectiveRepeatProtocol.IP_PROTO_SR, message);
                    messages.add(message);
                    nextSeqNum++;
                    message.sent = true;
                }
            }
        }
        for (SelectiveRepeatMessage message : messages) {
            if (message.acked) {
                if (message.seqNum == sendBase) {
                    sendBase++;
//                    System.out.println("Received : " + message.seqNum);
                }
            }
        }
        for (SelectiveRepeatMessage message : messages) {
            if (message.seqNum == receiveBase) {
                Receiver.dataList.add(message.data);
                receiveBase++;
            }
        }
    }

    public void send(SelectiveRepeatMessage message) throws Exception {
        queue.add(message);
        refresh();
    }

    public void timeout(SelectiveRepeatMessage message) throws Exception {
        RTO *= 2;
        message.timer.stop();
        message.timer = new Timer(host.getNetwork().getScheduler(), RTO, this, message);
        message.timer.start();
//        System.out.println("Timeout, resent : "+message.seqNum);
        host.getIPLayer().send(IPAddress.ANY, dst, SelectiveRepeatProtocol.IP_PROTO_SR, message);
        ssthresh = cwnd / 2;
        cwnd = MSS;
        congestionAvoidance=false; // Slow-start
//        System.out.println("ssthresh : "+ssthresh);
        System.out.println(host.getNetwork().getScheduler().getCurrentTime() + " : cwnd : "+cwnd);
        refresh();
    }

    @Override
    public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
        Message message = datagram.getPayload();
        if (message instanceof SelectiveRepeatMessage) {
            if (rand.nextInt(4) != 3) {
                int n = ((SelectiveRepeatMessage) message).seqNum;
                Ack ack = new Ack();
                ack.seqNum = n;
                host.getIPLayer().send(IPAddress.ANY, datagram.src, SelectiveRepeatProtocol.IP_PROTO_SR, ack);
                if (receiveBase <= n && n < receiveBase + cwnd) {
                    if (receiveBase == n) {
                        Receiver.dataList.add(((SelectiveRepeatMessage) message).data);
                        receiveBase++;
                    } else {
                        messages.add((SelectiveRepeatMessage) message);
                    }
                    refresh();
                }
            }
        } else if (message instanceof Ack) {
            arrTime = (host.getNetwork().scheduler.getCurrentTime());
            double r = arrTime - depTime;
            float alpha = 0.125f;
            float beta = 0.25f;
            SRTT = (SRTT == -1) ? r : (1 - alpha) * SRTT + alpha * (r);
            RTT = (RTT == -1) ? r / 2 : (1 - beta) * RTT + beta * (Math.abs(SRTT - r));
            RTO = SRTT + 4 * RTT;

            Ack ack = (Ack) message;
            if (sendBase <= ack.seqNum && ack.seqNum < sendBase + cwnd){
                for (SelectiveRepeatMessage m : messages) {
                    if (ack.seqNum == m.seqNum) {
                        m.timer.stop();
                        m.acked = true;
                        refresh();
                    }
                }
            }
            if (congestionAvoidance){
                cwnd += MSS * (MSS / cwnd);
            }
            else{
                cwnd += MSS;
                if (cwnd > ssthresh)
                    congestionAvoidance=true;
            }
            System.out.println(host.getNetwork().getScheduler().getCurrentTime() + " : cwnd : "+cwnd);
            System.out.println("ssthresh : "+ssthresh);
        }
        refresh();
    }
}
