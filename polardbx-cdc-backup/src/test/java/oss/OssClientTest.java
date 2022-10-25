/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.Bucket;
import com.aliyun.oss.model.BucketList;
import com.aliyun.oss.model.ListBucketsRequest;
import org.junit.Test;

import java.util.List;

public class OssClientTest {

    @Test
    public void test() {
        ListBucketsRequest request = new ListBucketsRequest("", null, null);
        OSS oss = new OSSClientBuilder().build("", "", "");
        BucketList bucketObj = oss.listBuckets(request);
        List<Bucket> bucketList = bucketObj.getBucketList();
        for (Bucket bucket : bucketList) {
            System.out.println(bucket.getName());
        }
    }
}
