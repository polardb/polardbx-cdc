/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import org.junit.Assert;
import org.junit.Test;

public class BlobFieldTest {
    private static final String defaultCharset = "utf8mb4";

    private static final String TOPICS_500 = "题目：我的家乡\n"
        + "我的家乡在湖南省涟源市，那里风景优美，物产丰富，是个可爱的地方。\n"
        + "涟源市位于湖南中部，那里有许多山，其中有一座叫做白马山，它高耸入云，直插云霄，从远处看上去就像一匹白色的马，因此得名。白马山上有许多古老的传说和古迹，是人们旅游的好去处。\n"
        + "涟源市有许多河流，其中有一条叫做涟水的河流，它发源于涟源市的西北部，全长一百多公里，是湖南省最长的河流之一。涟水河在涟源市境内有很多支流，河水清澈见底，河边绿树成荫，是人们游泳、野餐和散步的好去处。\n"
        + "涟源市是一个农业大市，那里有许多著名的农产品，其中有一种叫做涟源钢刀的刀具，远近驰名，是涟源市的特产之一。涟源市还是一个煤炭基地，那里有许多大型煤矿，为国家提供了大量的能源。\n"
        + "我的家乡还有很多特色小吃，其中有一种叫做米粉，是涟源市的传统美食，它口感滑爽，汤汁鲜美，是人们喜爱的食品之一。\n"
        + "我爱我的家乡，我相信你也会喜欢它的。";

    private static final byte[] TOPIC_500_BYTES = new byte[] {
        -125, 4, -23, -94, -104, -25, -101, -82, -17, -68, -102, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28,
        -71, -95, 10, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -27, -100, -88, -26, -71, -106,
        -27, -115, -105, -25, -100, -127, -26, -74, -97, -26, -70, -112, -27, -72, -126, -17, -68, -116, -23, -126, -93,
        -23, -121, -116, -23, -93, -114, -26, -103, -81, -28, -68, -104, -25, -66, -114, -17, -68, -116, -25, -119, -87,
        -28, -70, -89, -28, -72, -80, -27, -81, -116, -17, -68, -116, -26, -104, -81, -28, -72, -86, -27, -113, -81,
        -25, -120, -79, -25, -102, -124, -27, -100, -80, -26, -106, -71, -29, -128, -126, 10, -26, -74, -97, -26, -70,
        -112, -27, -72, -126, -28, -67, -115, -28, -70, -114, -26, -71, -106, -27, -115, -105, -28, -72, -83, -23, -125,
        -88, -17, -68, -116, -23, -126, -93, -23, -121, -116, -26, -100, -119, -24, -82, -72, -27, -92, -102, -27, -79,
        -79, -17, -68, -116, -27, -123, -74, -28, -72, -83, -26, -100, -119, -28, -72, -128, -27, -70, -89, -27, -113,
        -85, -27, -127, -102, -25, -103, -67, -23, -87, -84, -27, -79, -79, -17, -68, -116, -27, -82, -125, -23, -85,
        -104, -24, -128, -72, -27, -123, -91, -28, -70, -111, -17, -68, -116, -25, -101, -76, -26, -113, -110, -28, -70,
        -111, -23, -100, -124, -17, -68, -116, -28, -69, -114, -24, -65, -100, -27, -92, -124, -25, -100, -117, -28,
        -72, -118, -27, -114, -69, -27, -80, -79, -27, -125, -113, -28, -72, -128, -27, -116, -71, -25, -103, -67, -24,
        -119, -78, -25, -102, -124, -23, -87, -84, -17, -68, -116, -27, -101, -96, -26, -83, -92, -27, -66, -105, -27,
        -112, -115, -29, -128, -126, -25, -103, -67, -23, -87, -84, -27, -79, -79, -28, -72, -118, -26, -100, -119, -24,
        -82, -72, -27, -92, -102, -27, -113, -92, -24, -128, -127, -25, -102, -124, -28, -68, -96, -24, -81, -76, -27,
        -110, -116, -27, -113, -92, -24, -65, -71, -17, -68, -116, -26, -104, -81, -28, -70, -70, -28, -69, -84, -26,
        -105, -123, -26, -72, -72, -25, -102, -124, -27, -91, -67, -27, -114, -69, -27, -92, -124, -29, -128, -126, 10,
        -26, -74, -97, -26, -70, -112, -27, -72, -126, -26, -100, -119, -24, -82, -72, -27, -92, -102, -26, -78, -77,
        -26, -75, -127, -17, -68, -116, -27, -123, -74, -28, -72, -83, -26, -100, -119, -28, -72, -128, -26, -99, -95,
        -27, -113, -85, -27, -127, -102, -26, -74, -97, -26, -80, -76, -25, -102, -124, -26, -78, -77, -26, -75, -127,
        -17, -68, -116, -27, -82, -125, -27, -113, -111, -26, -70, -112, -28, -70, -114, -26, -74, -97, -26, -70, -112,
        -27, -72, -126, -25, -102, -124, -24, -91, -65, -27, -116, -105, -23, -125, -88, -17, -68, -116, -27, -123, -88,
        -23, -107, -65, -28, -72, -128, -25, -103, -66, -27, -92, -102, -27, -123, -84, -23, -121, -116, -17, -68, -116,
        -26, -104, -81, -26, -71, -106, -27, -115, -105, -25, -100, -127, -26, -100, -128, -23, -107, -65, -25, -102,
        -124, -26, -78, -77, -26, -75, -127, -28, -71, -117, -28, -72, -128, -29, -128, -126, -26, -74, -97, -26, -80,
        -76, -26, -78, -77, -27, -100, -88, -26, -74, -97, -26, -70, -112, -27, -72, -126, -27, -94, -125, -27, -122,
        -123, -26, -100, -119, -27, -66, -120, -27, -92, -102, -26, -108, -81, -26, -75, -127, -17, -68, -116, -26, -78,
        -77, -26, -80, -76, -26, -72, -123, -26, -66, -120, -24, -89, -127, -27, -70, -107, -17, -68, -116, -26, -78,
        -77, -24, -66, -71, -25, -69, -65, -26, -96, -111, -26, -120, -112, -24, -115, -85, -17, -68, -116, -26, -104,
        -81, -28, -70, -70, -28, -69, -84, -26, -72, -72, -26, -77, -77, -29, -128, -127, -23, -121, -114, -23, -92,
        -112, -27, -110, -116, -26, -107, -93, -26, -83, -91, -25, -102, -124, -27, -91, -67, -27, -114, -69, -27, -92,
        -124, -29, -128, -126, 10, -26, -74, -97, -26, -70, -112, -27, -72, -126, -26, -104, -81, -28, -72, -128, -28,
        -72, -86, -27, -122, -100, -28, -72, -102, -27, -92, -89, -27, -72, -126, -17, -68, -116, -23, -126, -93, -23,
        -121, -116, -26, -100, -119, -24, -82, -72, -27, -92, -102, -24, -111, -105, -27, -112, -115, -25, -102, -124,
        -27, -122, -100, -28, -70, -89, -27, -109, -127, -17, -68, -116, -27, -123, -74, -28, -72, -83, -26, -100, -119,
        -28, -72, -128, -25, -89, -115, -27, -113, -85, -27, -127, -102, -26, -74, -97, -26, -70, -112, -23, -110, -94,
        -27, -120, -128, -25, -102, -124, -27, -120, -128, -27, -123, -73, -17, -68, -116, -24, -65, -100, -24, -65,
        -111, -23, -87, -80, -27, -112, -115, -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70, -112, -27, -72,
        -126, -25, -102, -124, -25, -119, -71, -28, -70, -89, -28, -71, -117, -28, -72, -128, -29, -128, -126, -26, -74,
        -97, -26, -70, -112, -27, -72, -126, -24, -65, -104, -26, -104, -81, -28, -72, -128, -28, -72, -86, -25, -123,
        -92, -25, -126, -83, -27, -97, -70, -27, -100, -80, -17, -68, -116, -23, -126, -93, -23, -121, -116, -26, -100,
        -119, -24, -82, -72, -27, -92, -102, -27, -92, -89, -27, -98, -117, -25, -123, -92, -25, -97, -65, -17, -68,
        -116, -28, -72, -70, -27, -101, -67, -27, -82, -74, -26, -113, -112, -28, -66, -101, -28, -70, -122, -27, -92,
        -89, -23, -121, -113, -25, -102, -124, -24, -125, -67, -26, -70, -112, -29, -128, -126, 10, -26, -120, -111,
        -25, -102, -124, -27, -82, -74, -28, -71, -95, -24, -65, -104, -26, -100, -119, -27, -66, -120, -27, -92, -102,
        -25, -119, -71, -24, -119, -78, -27, -80, -113, -27, -112, -125, -17, -68, -116, -27, -123, -74, -28, -72, -83,
        -26, -100, -119, -28, -72, -128, -25, -89, -115, -27, -113, -85, -27, -127, -102, -25, -79, -77, -25, -78, -119,
        -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70, -112, -27, -72, -126, -25, -102, -124, -28, -68, -96,
        -25, -69, -97, -25, -66, -114, -23, -93, -97, -17, -68, -116, -27, -82, -125, -27, -113, -93, -26, -124, -97,
        -26, -69, -111, -25, -120, -67, -17, -68, -116, -26, -79, -92, -26, -79, -127, -23, -78, -100, -25, -66, -114,
        -17, -68, -116, -26, -104, -81, -28, -70, -70, -28, -69, -84, -27, -106, -100, -25, -120, -79, -25, -102, -124,
        -23, -93, -97, -27, -109, -127, -28, -71, -117, -28, -72, -128, -29, -128, -126, 10, -26, -120, -111, -25, -120,
        -79, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -17, -68, -116, -26, -120, -111, -25, -101,
        -72, -28, -65, -95, -28, -67, -96, -28, -71, -97, -28, -68, -102, -27, -106, -100, -26, -84, -94, -27, -82,
        -125, -25, -102, -124, -29, -128, -126};

    private static final String TOPIC_1000 = "题目：我的家乡\n"
        + "我的家乡在湖南省涟源市，那里风景优美，物产丰富，是个可爱的地方。\n"
        + "涟源市位于湖南中部，那里有许多山，其中有一座叫做白马山，它高耸入云，直插云霄，从远处看上去就像一匹白色的马，因此得名。白马山上有许多古老的传说和古迹，是人们旅游的好去处。\n"
        + "涟源市有许多河流，其中有一条叫做涟水的河流，它发源于涟源市的西北部，全长一百多公里，是湖南省最长的河流之一。涟水河在涟源市境内有很多支流，河水清澈见底，河边绿树成荫，是人们游泳、野餐和散步的好去处。\n"
        + "涟源市是一个农业大市，那里有许多著名的农产品，其中有一种叫做涟源钢刀的刀具，远近驰名，是涟源市的特产之一。涟源市还是一个煤炭基地，那里有许多大型煤矿，为国家提供了大量的能源。\n"
        + "我的家乡还有很多特色小吃，其中有一种叫做米粉，是涟源市的传统美食，它口感滑爽，汤汁鲜美，是人们喜爱的食品之一。\n"
        + "我的家乡还有很多特色民俗，其中有一种叫做涟源花鼓，是涟源市的传统民间艺术，它的表演形式是一群人穿着古装，手持花鼓，边唱边跳，非常热闹。涟源花鼓的表演有很多规矩，表演者必须严格遵守，才能表演出好的效果。\n"
        + "我的家乡还有很多特色节日，其中有一个叫做涟源市的端午节，是涟源市的传统节日之一，每年的端午节，人们都会穿上古装，手持花鼓，边唱边跳，庆祝节日。涟源市的端午节也是涟源市最热闹的节日之一，每年都有大量的人前来参加。\n"
        + "我的家乡是一个可爱的地方，它有着美丽的风景，丰富的物产，特色的小吃，特色的民俗，特色的节日，是一个人们向往的地方。我爱我的家乡，我相信你也会喜欢它的。";
    private static final byte[] TOPIC_1000_BYTES = new byte[] {
        -105, 7, 0, 0, -23, -94, -104, -25, -101, -82, -17, -68, -102, -26, -120, -111, -25, -102, -124, -27, -82, -74,
        -28, -71, -95, 10, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -27, -100, -88, -26, -71,
        -106, -27, -115, -105, -25, -100, -127, -26, -74, -97, -26, -70, -112, -27, -72, -126, -17, -68, -116, -23,
        -126, -93, -23, -121, -116, -23, -93, -114, -26, -103, -81, -28, -68, -104, -25, -66, -114, -17, -68, -116, -25,
        -119, -87, -28, -70, -89, -28, -72, -80, -27, -81, -116, -17, -68, -116, -26, -104, -81, -28, -72, -86, -27,
        -113, -81, -25, -120, -79, -25, -102, -124, -27, -100, -80, -26, -106, -71, -29, -128, -126, 10, -26, -74, -97,
        -26, -70, -112, -27, -72, -126, -28, -67, -115, -28, -70, -114, -26, -71, -106, -27, -115, -105, -28, -72, -83,
        -23, -125, -88, -17, -68, -116, -23, -126, -93, -23, -121, -116, -26, -100, -119, -24, -82, -72, -27, -92, -102,
        -27, -79, -79, -17, -68, -116, -27, -123, -74, -28, -72, -83, -26, -100, -119, -28, -72, -128, -27, -70, -89,
        -27, -113, -85, -27, -127, -102, -25, -103, -67, -23, -87, -84, -27, -79, -79, -17, -68, -116, -27, -82, -125,
        -23, -85, -104, -24, -128, -72, -27, -123, -91, -28, -70, -111, -17, -68, -116, -25, -101, -76, -26, -113, -110,
        -28, -70, -111, -23, -100, -124, -17, -68, -116, -28, -69, -114, -24, -65, -100, -27, -92, -124, -25, -100,
        -117, -28, -72, -118, -27, -114, -69, -27, -80, -79, -27, -125, -113, -28, -72, -128, -27, -116, -71, -25, -103,
        -67, -24, -119, -78, -25, -102, -124, -23, -87, -84, -17, -68, -116, -27, -101, -96, -26, -83, -92, -27, -66,
        -105, -27, -112, -115, -29, -128, -126, -25, -103, -67, -23, -87, -84, -27, -79, -79, -28, -72, -118, -26, -100,
        -119, -24, -82, -72, -27, -92, -102, -27, -113, -92, -24, -128, -127, -25, -102, -124, -28, -68, -96, -24, -81,
        -76, -27, -110, -116, -27, -113, -92, -24, -65, -71, -17, -68, -116, -26, -104, -81, -28, -70, -70, -28, -69,
        -84, -26, -105, -123, -26, -72, -72, -25, -102, -124, -27, -91, -67, -27, -114, -69, -27, -92, -124, -29, -128,
        -126, 10, -26, -74, -97, -26, -70, -112, -27, -72, -126, -26, -100, -119, -24, -82, -72, -27, -92, -102, -26,
        -78, -77, -26, -75, -127, -17, -68, -116, -27, -123, -74, -28, -72, -83, -26, -100, -119, -28, -72, -128, -26,
        -99, -95, -27, -113, -85, -27, -127, -102, -26, -74, -97, -26, -80, -76, -25, -102, -124, -26, -78, -77, -26,
        -75, -127, -17, -68, -116, -27, -82, -125, -27, -113, -111, -26, -70, -112, -28, -70, -114, -26, -74, -97, -26,
        -70, -112, -27, -72, -126, -25, -102, -124, -24, -91, -65, -27, -116, -105, -23, -125, -88, -17, -68, -116, -27,
        -123, -88, -23, -107, -65, -28, -72, -128, -25, -103, -66, -27, -92, -102, -27, -123, -84, -23, -121, -116, -17,
        -68, -116, -26, -104, -81, -26, -71, -106, -27, -115, -105, -25, -100, -127, -26, -100, -128, -23, -107, -65,
        -25, -102, -124, -26, -78, -77, -26, -75, -127, -28, -71, -117, -28, -72, -128, -29, -128, -126, -26, -74, -97,
        -26, -80, -76, -26, -78, -77, -27, -100, -88, -26, -74, -97, -26, -70, -112, -27, -72, -126, -27, -94, -125,
        -27, -122, -123, -26, -100, -119, -27, -66, -120, -27, -92, -102, -26, -108, -81, -26, -75, -127, -17, -68,
        -116, -26, -78, -77, -26, -80, -76, -26, -72, -123, -26, -66, -120, -24, -89, -127, -27, -70, -107, -17, -68,
        -116, -26, -78, -77, -24, -66, -71, -25, -69, -65, -26, -96, -111, -26, -120, -112, -24, -115, -85, -17, -68,
        -116, -26, -104, -81, -28, -70, -70, -28, -69, -84, -26, -72, -72, -26, -77, -77, -29, -128, -127, -23, -121,
        -114, -23, -92, -112, -27, -110, -116, -26, -107, -93, -26, -83, -91, -25, -102, -124, -27, -91, -67, -27, -114,
        -69, -27, -92, -124, -29, -128, -126, 10, -26, -74, -97, -26, -70, -112, -27, -72, -126, -26, -104, -81, -28,
        -72, -128, -28, -72, -86, -27, -122, -100, -28, -72, -102, -27, -92, -89, -27, -72, -126, -17, -68, -116, -23,
        -126, -93, -23, -121, -116, -26, -100, -119, -24, -82, -72, -27, -92, -102, -24, -111, -105, -27, -112, -115,
        -25, -102, -124, -27, -122, -100, -28, -70, -89, -27, -109, -127, -17, -68, -116, -27, -123, -74, -28, -72, -83,
        -26, -100, -119, -28, -72, -128, -25, -89, -115, -27, -113, -85, -27, -127, -102, -26, -74, -97, -26, -70, -112,
        -23, -110, -94, -27, -120, -128, -25, -102, -124, -27, -120, -128, -27, -123, -73, -17, -68, -116, -24, -65,
        -100, -24, -65, -111, -23, -87, -80, -27, -112, -115, -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70,
        -112, -27, -72, -126, -25, -102, -124, -25, -119, -71, -28, -70, -89, -28, -71, -117, -28, -72, -128, -29, -128,
        -126, -26, -74, -97, -26, -70, -112, -27, -72, -126, -24, -65, -104, -26, -104, -81, -28, -72, -128, -28, -72,
        -86, -25, -123, -92, -25, -126, -83, -27, -97, -70, -27, -100, -80, -17, -68, -116, -23, -126, -93, -23, -121,
        -116, -26, -100, -119, -24, -82, -72, -27, -92, -102, -27, -92, -89, -27, -98, -117, -25, -123, -92, -25, -97,
        -65, -17, -68, -116, -28, -72, -70, -27, -101, -67, -27, -82, -74, -26, -113, -112, -28, -66, -101, -28, -70,
        -122, -27, -92, -89, -23, -121, -113, -25, -102, -124, -24, -125, -67, -26, -70, -112, -29, -128, -126, 10, -26,
        -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -24, -65, -104, -26, -100, -119, -27, -66, -120, -27,
        -92, -102, -25, -119, -71, -24, -119, -78, -27, -80, -113, -27, -112, -125, -17, -68, -116, -27, -123, -74, -28,
        -72, -83, -26, -100, -119, -28, -72, -128, -25, -89, -115, -27, -113, -85, -27, -127, -102, -25, -79, -77, -25,
        -78, -119, -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70, -112, -27, -72, -126, -25, -102, -124, -28,
        -68, -96, -25, -69, -97, -25, -66, -114, -23, -93, -97, -17, -68, -116, -27, -82, -125, -27, -113, -93, -26,
        -124, -97, -26, -69, -111, -25, -120, -67, -17, -68, -116, -26, -79, -92, -26, -79, -127, -23, -78, -100, -25,
        -66, -114, -17, -68, -116, -26, -104, -81, -28, -70, -70, -28, -69, -84, -27, -106, -100, -25, -120, -79, -25,
        -102, -124, -23, -93, -97, -27, -109, -127, -28, -71, -117, -28, -72, -128, -29, -128, -126, 10, -26, -120,
        -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -24, -65, -104, -26, -100, -119, -27, -66, -120, -27, -92,
        -102, -25, -119, -71, -24, -119, -78, -26, -80, -111, -28, -65, -105, -17, -68, -116, -27, -123, -74, -28, -72,
        -83, -26, -100, -119, -28, -72, -128, -25, -89, -115, -27, -113, -85, -27, -127, -102, -26, -74, -97, -26, -70,
        -112, -24, -118, -79, -23, -68, -109, -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70, -112, -27, -72,
        -126, -25, -102, -124, -28, -68, -96, -25, -69, -97, -26, -80, -111, -23, -105, -76, -24, -119, -70, -26, -100,
        -81, -17, -68, -116, -27, -82, -125, -25, -102, -124, -24, -95, -88, -26, -68, -108, -27, -67, -94, -27, -68,
        -113, -26, -104, -81, -28, -72, -128, -25, -66, -92, -28, -70, -70, -25, -87, -65, -25, -99, -128, -27, -113,
        -92, -24, -93, -123, -17, -68, -116, -26, -119, -117, -26, -116, -127, -24, -118, -79, -23, -68, -109, -17, -68,
        -116, -24, -66, -71, -27, -108, -79, -24, -66, -71, -24, -73, -77, -17, -68, -116, -23, -99, -98, -27, -72, -72,
        -25, -125, -83, -23, -105, -71, -29, -128, -126, -26, -74, -97, -26, -70, -112, -24, -118, -79, -23, -68, -109,
        -25, -102, -124, -24, -95, -88, -26, -68, -108, -26, -100, -119, -27, -66, -120, -27, -92, -102, -24, -89, -124,
        -25, -97, -87, -17, -68, -116, -24, -95, -88, -26, -68, -108, -24, -128, -123, -27, -65, -123, -23, -95, -69,
        -28, -72, -91, -26, -96, -68, -23, -127, -75, -27, -82, -120, -17, -68, -116, -26, -119, -115, -24, -125, -67,
        -24, -95, -88, -26, -68, -108, -27, -121, -70, -27, -91, -67, -25, -102, -124, -26, -107, -120, -26, -98, -100,
        -29, -128, -126, 10, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -24, -65, -104, -26, -100,
        -119, -27, -66, -120, -27, -92, -102, -25, -119, -71, -24, -119, -78, -24, -118, -126, -26, -105, -91, -17, -68,
        -116, -27, -123, -74, -28, -72, -83, -26, -100, -119, -28, -72, -128, -28, -72, -86, -27, -113, -85, -27, -127,
        -102, -26, -74, -97, -26, -70, -112, -27, -72, -126, -25, -102, -124, -25, -85, -81, -27, -115, -120, -24, -118,
        -126, -17, -68, -116, -26, -104, -81, -26, -74, -97, -26, -70, -112, -27, -72, -126, -25, -102, -124, -28, -68,
        -96, -25, -69, -97, -24, -118, -126, -26, -105, -91, -28, -71, -117, -28, -72, -128, -17, -68, -116, -26, -81,
        -113, -27, -71, -76, -25, -102, -124, -25, -85, -81, -27, -115, -120, -24, -118, -126, -17, -68, -116, -28, -70,
        -70, -28, -69, -84, -23, -125, -67, -28, -68, -102, -25, -87, -65, -28, -72, -118, -27, -113, -92, -24, -93,
        -123, -17, -68, -116, -26, -119, -117, -26, -116, -127, -24, -118, -79, -23, -68, -109, -17, -68, -116, -24,
        -66, -71, -27, -108, -79, -24, -66, -71, -24, -73, -77, -17, -68, -116, -27, -70, -122, -25, -91, -99, -24,
        -118, -126, -26, -105, -91, -29, -128, -126, -26, -74, -97, -26, -70, -112, -27, -72, -126, -25, -102, -124,
        -25, -85, -81, -27, -115, -120, -24, -118, -126, -28, -71, -97, -26, -104, -81, -26, -74, -97, -26, -70, -112,
        -27, -72, -126, -26, -100, -128, -25, -125, -83, -23, -105, -71, -25, -102, -124, -24, -118, -126, -26, -105,
        -91, -28, -71, -117, -28, -72, -128, -17, -68, -116, -26, -81, -113, -27, -71, -76, -23, -125, -67, -26, -100,
        -119, -27, -92, -89, -23, -121, -113, -25, -102, -124, -28, -70, -70, -27, -119, -115, -26, -99, -91, -27, -113,
        -126, -27, -118, -96, -29, -128, -126, 10, -26, -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -26,
        -104, -81, -28, -72, -128, -28, -72, -86, -27, -113, -81, -25, -120, -79, -25, -102, -124, -27, -100, -80, -26,
        -106, -71, -17, -68, -116, -27, -82, -125, -26, -100, -119, -25, -99, -128, -25, -66, -114, -28, -72, -67, -25,
        -102, -124, -23, -93, -114, -26, -103, -81, -17, -68, -116, -28, -72, -80, -27, -81, -116, -25, -102, -124, -25,
        -119, -87, -28, -70, -89, -17, -68, -116, -25, -119, -71, -24, -119, -78, -25, -102, -124, -27, -80, -113, -27,
        -112, -125, -17, -68, -116, -25, -119, -71, -24, -119, -78, -25, -102, -124, -26, -80, -111, -28, -65, -105,
        -17, -68, -116, -25, -119, -71, -24, -119, -78, -25, -102, -124, -24, -118, -126, -26, -105, -91, -17, -68,
        -116, -26, -104, -81, -28, -72, -128, -28, -72, -86, -28, -70, -70, -28, -69, -84, -27, -112, -111, -27, -66,
        -128, -25, -102, -124, -27, -100, -80, -26, -106, -71, -29, -128, -126, -26, -120, -111, -25, -120, -79, -26,
        -120, -111, -25, -102, -124, -27, -82, -74, -28, -71, -95, -17, -68, -116, -26, -120, -111, -25, -101, -72, -28,
        -65, -95, -28, -67, -96, -28, -71, -97, -28, -68, -102, -27, -106, -100, -26, -84, -94, -27, -82, -125, -25,
        -102, -124, -29, -128, -126};

    @Test
    public void testTinyText() {
        Field field = MakeFieldFactory.makeField("tinytext", "1", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1, 49}, field.encode());
    }

    @Test
    public void testMediumText() {
        Field field = MakeFieldFactory.makeField("mediumtext", "1", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1, 0, 0, 49}, field.encode());
    }

    @Test
    public void testLongText() {
        Field field = MakeFieldFactory.makeField("longtext", TOPIC_1000, defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(TOPIC_1000_BYTES, field.encode());
    }

    @Test
    public void testText() {
        System.out.println(TOPICS_500);
        Field field = MakeFieldFactory.makeField("text", TOPICS_500, defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {2}, field.doGetTableMeta());
        Assert.assertArrayEquals(TOPIC_500_BYTES, field.encode());
    }

    @Test
    public void testTinyBlob() {
        Field field =
            MakeFieldFactory.makeField("tinyblob", "0x55555555555555555555555555555555", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {16, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85},
            field.encode());
    }

    @Test
    public void testMediumBlob() {
        Field field =
            MakeFieldFactory.makeField("mediumblob", "0x55555555555555555555555555555555", defaultCharset, false,
                false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {16, 0, 0, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85},
            field.encode());
    }

    @Test
    public void testLongBlob() {
        Field field =
            MakeFieldFactory.makeField("longblob", "0x55555555555555555555555555555555", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {16, 0, 0, 0, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85}, field.encode());
    }

    @Test
    public void testBlob() {
        Field field =
            MakeFieldFactory.makeField("blob", "0x55555555555555555555555555555555", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {16, 0, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85},
            field.encode());
    }

    @Test
    public void testTinyBlob1() {
        Field field =
            MakeFieldFactory.makeField("tinyblob", "b'1010101010101010101'", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3, 5, 85, 85},
            field.encode());
    }

    @Test
    public void testMediumBlob1() {
        Field field =
            MakeFieldFactory.makeField("mediumblob", "b'1010101010101010101'", defaultCharset, false,
                false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3, 0, 0, 5, 85, 85},
            field.encode());
    }

    @Test
    public void testLongBlob1() {
        Field field =
            MakeFieldFactory.makeField("longblob", "b'1010101010101010101'", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {3, 0, 0, 0, 5, 85, 85}, field.encode());
    }

    @Test
    public void testBlob1() {
        Field field =
            MakeFieldFactory.makeField("blob", "b'1010101010101010101'", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3, 0, 5, 85, 85},
            field.encode());
    }

    @Test
    public void testTextNullValue() {
        Field field =
            MakeFieldFactory.makField4TypeMisMatch("text", "null", defaultCharset, false, "null", false);
        Assert.assertArrayEquals(new byte[] {2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {4, 0, 110, 117, 108, 108},
            field.encode());
    }

    @Test
    public void testTextNull() {
        Field field =
            MakeFieldFactory.makField4TypeMisMatch("text", null, defaultCharset, false, "null", false);
        Assert.assertArrayEquals(new byte[] {2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {},
            field.encode());
    }
}
