/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class Cursor implements Comparable<Cursor> {

    private String fileName;
    private Long filePosition;

    public Cursor(String fileName, Long filePosition) {
        this.fileName = fileName;
        this.filePosition = filePosition;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFilePosition() {
        return filePosition;
    }

    public void setFilePosition(Long filePosition) {
        this.filePosition = filePosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Cursor cursor = (Cursor) o;
        return fileName.equals(cursor.fileName) &&
            filePosition.equals(cursor.filePosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, filePosition);
    }

    @Override
    public int compareTo(Cursor o) {
        if (o == null) {
            return 1;
        } else {
            int flag = fileName.compareTo(o.fileName);
            if (flag != 0) {
                return flag;
            } else {
                return filePosition.compareTo(o.filePosition);
            }
        }
    }

    @Override
    public String toString() {
        return "Cursor{" + "fileName='" + fileName + '\'' + ", filePosition=" + filePosition + '}';
    }

    public static void main(String args[]) {
        List<Cursor> list = new ArrayList<>();
        list.add(new Cursor("binlog.000003", 500L));
        list.add(new Cursor("binlog.000001", 100L));
        list.add(new Cursor("binlog.000002", 600L));
        list.add(new Cursor("binlog.000001", 200L));
        list.add(new Cursor("binlog.000002", 100L));
        list.add(new Cursor("binlog.000001", 100L));

        list = list.stream().sorted().collect(Collectors.toList());
        list.forEach(System.out::println);
    }
}
