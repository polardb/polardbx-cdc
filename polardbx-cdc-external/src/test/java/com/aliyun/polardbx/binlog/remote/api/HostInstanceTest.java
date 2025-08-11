/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api;

import com.aliyun.polardbx.binlog.api.HostInstance;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HostInstanceTest {
    @Test
    public void duplicateAddBinlog() throws ParseException {
        HostInstance instance = new HostInstance();
        BinlogFile binlogFile = new BinlogFile();
        binlogFile.setLogname("a");
        binlogFile.setInstanceID(1L);
        binlogFile.setBeginTime(1L);
        binlogFile.setEndTime(2L);
        instance.addBinlog(binlogFile);
        instance.addBinlog(binlogFile);
        List<BinlogFile> binlogFiles = instance.getBinlogFiles();
        assertEquals(1, binlogFiles.size());
        assertEquals(binlogFile, binlogFiles.get(0));
    }

    @Test(expected = PolardbxException.class)
    public void getServerIdExceptionTest() {
        HostInstance instance = new HostInstance();
        instance.getServerId();
    }

    @Test
    public void testExtractServerId_ServerIdNotNull() {
        // Mock the behavior of BinlogFile
        long serverId = 12345L;
        BinlogFile binlogFile = mock(BinlogFile.class);
        when(binlogFile.getServerId()).thenReturn(serverId);
        HostInstance hostInstance = new HostInstance();

        // Call the method under test
        long result = hostInstance.extractServerId(binlogFile);

        // Verify interactions
        verify(binlogFile, times(2)).getServerId();
        Assert.assertEquals(serverId, result);
    }

    @Test
    public void testExtractServerId_ServerIdNull_Success() throws IOException, MalformedURLException {
        // Mock the behavior of BinlogFile
        String url = "http://example.com/binlog";
        String downloadLink = "http://example.com/download";
        BinlogFile binlogFile = mock(BinlogFile.class);
        HostInstance hostInstance = mock(HostInstance.class);
        when(binlogFile.getServerId()).thenReturn(null);
        when(binlogFile.getIntranetDownloadLink()).thenReturn(url);
        when(binlogFile.getInstanceID()).thenReturn(1L);
        when(binlogFile.getIntranetDownloadLink()).thenReturn(downloadLink);

        // Mock the behavior of HttpURLConnection and InputStream
        byte[] mockData = new byte[20];
        mockData[9] = (byte) 0x12;
        mockData[10] = (byte) 0x34;
        mockData[11] = (byte) 0x56;
        mockData[12] = (byte) 0x78;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mockData);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);

        when(hostInstance.getConnection(any())).thenReturn(connection);
        when(connection.getInputStream()).thenReturn(inputStream);
        when(hostInstance.extractServerId(any())).thenCallRealMethod();

        // Call the method under test
        long result = hostInstance.extractServerId(binlogFile);

        // Verify interactions
        verify(binlogFile).getServerId();
        verify(binlogFile, times(2)).getIntranetDownloadLink();
        verify(binlogFile).getInstanceID();
        verify(connection).connect();
        verify(connection).getInputStream();

        // Verify the result
        assertEquals(0x78563412L, result);
    }

    private BinlogFile newBinlogFile(String fileName, long serverId, long instanceId){
        BinlogFile binlogFile = new BinlogFile();
        binlogFile.setLogname(fileName);
        binlogFile.setServerId(serverId);
        binlogFile.setInstanceID(instanceId);
        return binlogFile;
    }

    @Test
    public void testBinlogListSorter() throws ParseException {
        HostInstance hostInstance = new HostInstance();
        BinlogFile binlogFile1 = newBinlogFile("bin.101", 1, 1);
        BinlogFile binlogFile2 = newBinlogFile("bin.1001", 1, 1);
        BinlogFile binlogFile3 = newBinlogFile("bin.11", 1, 1);

        hostInstance.addBinlog(binlogFile1);
        hostInstance.addBinlog(binlogFile2);
        hostInstance.addBinlog(binlogFile3);

        List<BinlogFile> fileList = new ArrayList<>();
        fileList.add(binlogFile3);
        fileList.add(binlogFile1);
        fileList.add(binlogFile2);
        Assert.assertEquals(fileList, hostInstance.sortList());
    }

}
