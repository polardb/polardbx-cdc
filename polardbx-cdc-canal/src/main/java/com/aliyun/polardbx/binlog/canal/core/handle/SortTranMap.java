/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ITranStatChangeListener;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.google.common.collect.Maps;
import lombok.Data;

import java.util.Map;

public class SortTranMap implements ITranStatChangeListener {

    private Node head;
    private Node tail;
    private Long baseTSO;
    private Map<String, Node> xidNodeMap = Maps.newHashMap();

    public SortTranMap(Long baseTSO) {
        this.baseTSO = baseTSO;
    }

    public void add(TranPosition tran) {
        Node n = new Node();
        n.tranPosition = tran;
        tran.registerStateChangeListener(this);
        xidNodeMap.put(n.getXid(), n);
        link(n);
    }

    public void link(Node n) {
        if (head == null) {
            head = tail = n;
        } else {
            tail.next = n;
            n.pre = tail;
            tail = n;
        }
        n.position = head.tranPosition.getPosition();
    }

    public TranPosition get(String xid) {
        Node n = xidNodeMap.get(xid);
        if (n == null) {
            return null;
        }
        return n.tranPosition;
    }

    public boolean remove(String xid) {
        Node n = xidNodeMap.remove(xid);
        if (n != null) {
            remove(n);
            return true;
        }
        return false;
    }

    public void clear() {
        this.xidNodeMap.clear();
        this.head = tail = null;
    }

    public int size() {
        return xidNodeMap.size();
    }

    /**
     * p1 p2 c1 p3 p4 c4 c2 c3
     * 1、 p1   position : p1
     * 2、 p2   position : p1
     * 3、 c1   remove t1
     * 4、 p3   position : p2
     * 5、 p4   position : p2
     * 6、 c4   remove t4
     * 7、 c2   remove t2
     * 8、 c3   remove t3
     */
    private void remove(Node n) {

        if (tail == head) {
            head = tail = null;
        } else if (n == tail) {
            tail = tail.pre;
            tail.next = null;
        } else if (n == head) {
            head = head.next;
            head.pre = null;
        } else {
            Node pre = n.pre;
            Node next = n.next;
            if (pre != null) {
                pre.next = next;
            }
            if (next != null) {
                next.pre = pre;
            }
        }

    }

    private Node findMax() {
        return tail;
    }

    private Node findMin() {
        return head;
    }

    public BinlogPosition getMinPos(String rtso) {
        Node n = findMin();
        n.tranPosition.setRtso(rtso);
        return n.getPosition();
    }

    @Override
    public void onComplete(TranPosition position) {
        remove(position.getXid());
    }

    @Data
    private class Node implements Comparable<Node> {
        Node pre;
        Node next;
        TranPosition tranPosition;
        // 最小position
        BinlogPosition position;

        public String getXid() {
            return tranPosition.getXid();
        }

        /**
         * 返回加入链表时,head的position， tso可以用当前的，这里的tso只是用来回溯schema信息，空洞阶段理解应该没有ddl。
         */
        public BinlogPosition getPosition() {
            position.setRtso(tranPosition.getRtso());
            return position;
        }

        @Override
        public int compareTo(Node o) {
            return tranPosition.getPosition().compareTo(o.tranPosition.getPosition());
        }
    }
}
