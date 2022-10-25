namespace java com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated

struct Bucket {
  //bucket name
  1: required string name,
  //create time
  2: required string createTime,
  //bucket type
  3: required string bucketType
}

struct Authorization{
	//access key id
	1: required string accessKeyID,
	//sign by access key id, access key secret, timestamp
	2: required string signature,
	//sign timestamp
	3: required i64 timestamp,
	//stsToken
	4: optional string stsToken
}

struct FileInfo{
	//file path
	1: optional string path,
	//bucket name
	2: optional string bucket,
	//file size
	3: optional i64 size,
	//file modify time
	4: optional string modifyTime,
	//file crc64 code
	5: optional i64 crc64,
	//file customize meta
    6: optional map<string, binary> customizeMetas,
	//file tags
	7: optional map<string, binary> tags
}

struct CreateFileResponse{
	//file path
	1: required string path,
	//bucket name
	2: required string bucket,
	//output stream id
	3: required string outputStreamId
}

struct WriteFileResponse{
	//file path
	1: required string path,
	//bucket name
	2: required string bucket,
	//output stream id
	3: required string outputStreamId,
	//next offset
	4: required i64 nextOffset
}

struct FlushFileResponse{
	//file path
	1: required string path,
	//bucket name
	2: required string bucket,
	//output stream id
	3: required string outputStreamId,
	//visiable offset
	4: required i64 visiableOffset,
	//visiable part crc64
	5: required i64 crc64
}

struct ReadFileResponse{
	//file path
	1: required string path,
	//bucket name
	2: required string bucket,
	//read result
	3: required binary result,
	//read size
	4: required i64 size,
	//read part crc64 code
	5: required i64 crc64,
	//has more or not
	6: required bool hasMore
}

struct CreateBucketRequest{
	//bucket name
	1: required string bucketName,
	//authorization info
	2: required Authorization auth
}

struct ListBucketsRequest{
	//authorization info
	1: required Authorization auth
}

struct DeleteBucketRequest{
	//bucket name
	1: required string bucketName,
	//authorization info
	2: required Authorization auth
}

struct CreateFileRequest{
	//bucket name
	1: required string bucketName,
	//file path
	2: required string path,
	//file customize meta
    3: optional map<string, binary> customizeMetas,
	//file tags, user can set custom tag, key is tag name, value is tag value
	4: optional map<string, binary> tags,
	//authorization info
	5: required Authorization auth
	//append or not, default is false;
    6: optional bool isAppend
}

struct WriteFileRequest{
	//outputStreamId output stream id
	1: required string outputStreamId,
	//bucket name
    2: required string bucketName,
    //file path
    3: required string path,
	//src data
	4: required binary src,
	//srcOffset position start read from src
	5: required i32 srcOffset,
	//srcLength the length of data read from src
	6: required i32 srcLength,
	//position start to write
	7: required i64 fileOffset,
	//crc64 code of new data for check value integrity
	8: required i64 fileCrc64,
	//authorization info
	9: required Authorization auth
}

struct FlushFileRequest{
	//output stream id
	1: required string outputStreamId,
	//bucket name
    2: required string bucketName,
    //file path
    3: required string path,
	//offset position to flush, then 0 to position data visiable
	4: required i64 offset,
	//authorization info
	5: required Authorization auth
}

struct CompleteFileRequest{
	//output stream id
	1: required string outputStreamId,
    //bucket name
    2: required string bucketName,
    //file path
    3: required string path,
	//auth authorization info
	4: required Authorization auth
}

struct ReadFileRequest{
	//bucket name
	1: required string bucketName,
	//file path
	2: required string path,
	//offset position start read file
	3: required i64 offset,
	//size of file to read
	4: required i32 size,
	//authorization info
	5: required Authorization auth
}

struct PutFileRequest{
	//bucket name
	1: required string bucketName,
	//file path
	2: required string path,
	//fileContent file content
	3: required binary fileContent,
	//crc64 code check file integrity
	4: required i64 crc64,
	//file customize meta
    5: optional map<string, binary> customizeMetas,
	//file tags, user can set custom tag, key is tag name, value is tag value
	6: optional map<string, binary> tags,
	//authorization info
	7: required Authorization auth
	//append or not
	8: optional bool append
}

struct DeleteFileRequest{
    //bucket name
    1: required string bucketName,
    //file path
    2: required list<string> paths,
    //authorization info
    3: required Authorization auth
}


struct GetFileInfoRequest{
    //bucket name
    1: required string bucketName,
    //file path
    2: required string filePath,
    //authorization info
    3: required Authorization auth
}

struct ListFileInfosRequest{
    //bucket name
    1: required string bucketName,
    //list files in path prefix
    2: optional string pathPrefix,
    //limit return result number
    3: optional i32 limit,
    //list files after startPath
    4: optional string startPath,
    //authorization info
    5: required Authorization auth
}

//
// Exceptions
//

exception IOError {
  1: required string message
}

exception AuthorizationError {
  1: required string message
}


service LindormFileService{
	/**
     * Create Bucket;
     *
     * All files storage in a bucket, like namespace
     *
     * @param createBucketRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when create bucket failed
     * @return Bucket info
     */
    Bucket createBucket(
    	1: required CreateBucketRequest createBucketRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * List All Bucket you have;
     *
     * @param listBucketsRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when list buckets failed
     * @return list of bucket you have
     */
    list<Bucket> listBuckets(
    	1: required ListBucketsRequest listBucketsRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Delete Bucket;
     *
     * @param deleteBucketRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when delete bucket failed
     */
    void deleteBucket(
    	1: required DeleteBucketRequest deleteBucketRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Create File
     *
     * If you want create a large size file ,you need create file first, then write data, flush and complete
     *
     * @param createFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when create file failed
     * @return create file response
     */
    CreateFileResponse createFile(
    	1: required CreateFileRequest createFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )
    /**
     * Write File
     *
     * Write new data to an opened file
     *
     * @param writeFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when write file failed
     * @return write file response
     */
    WriteFileResponse writeFile(
    	1: required WriteFileRequest writeFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Flush File
     *
     * By flush, make data visiable which already write into file
     *
     * @param flushFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when flush file failed
     * @return flush file response
     */
    FlushFileResponse flushFile(
    	1: required FlushFileRequest flushFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Complete File
     *
     * After complete, the file is immutable
     *
     * @param completeFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when complete file failed
     * @return FileInfo
     */
    FileInfo completeFile(
    	1: required CompleteFileRequest completeFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Read File
     *
     * @param readFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when read file failed
     * @return read file response
     */
    ReadFileResponse readFile(
    	1: required ReadFileRequest readFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Put file use for little size file(size < 10MB)
     *
     * @param putFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when put fail failed
     */
    FileInfo putFile(
    	1: required PutFileRequest putFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Delete File
     *
     * @param deleteFileRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when delete file failed
     */
    void deleteFile(
    	1: required DeleteFileRequest deleteFileRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * Get File Info
     *
     * @param getFileInfoRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when get file info failed
     * @return FileInfo
     */
    FileInfo getFileInfo(
    	1: required GetFileInfoRequest getFileInfoRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )

    /**
     * List File Infos
     *
     * @param listFileInfosRequest
     * @exception AuthorizationException throw AuthorizationException when authorization failed
     * @exception IOException throw IOException when list file infos failed
     * @return list of file info
     */
    list<FileInfo> listFileInfos(
    	1: required ListFileInfosRequest listFileInfosRequest
    ) throws (
    	1: AuthorizationError authError,
    	2: IOError io
    )
}
