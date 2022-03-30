package com.aliyun.polardbx.binlog.cdc.meta;

/**
 * Rollback时，元数据的构建方式
 **/
public enum RollbackMode {
    /**
     * 逻辑表和物理表，都使用基于[一致的SNAPSHOT]的事件溯源模式进行元数据构建，真正意义上的基于镜像进行数据溯源的模式
     * <p>
     * 1. Snapshot：和时间线关联的一份全量元数据镜像，该镜像对应的元数据一定是保证[全局数据一致]的(即镜像所在的时间点，逻辑表和物理表结构一致，拓扑一致)，是一个安全点
     * 2. 首先基于最近一次的Snapshot构建出一份全量元数据(逻辑+物理)，然后重放该Snapshot之后的所有小于等于rollback tso的Logic&&Physical DDL Event
     * 3. Baseline是一种特殊的Snapshot，如果不存在除了Baseline的其它Snapshot，则相当于每次都从CDC初始化的时间开始重放构建
     */
    SNAPSHOT_EXACTLY,

    /**
     * 轻量级的Snapshot，或者说它只是一个Snapshot Point，不具备全局一致性，作用域限定在某个Storage。基本原理如下：Extractor每执行完一个逻辑DDL之后，会对逻辑表和物理表的
     * 元数据进行一次“Consistenty Check”，如果一致，则认为是一个Snapshot Point(这个Snapshot针对当前这个Storage有效)，反正则忽略；每次Rollback的(Rollback的点记为RP)，
     * 会查找离rollback点最近的一次Snapshot Point(记为SP)，然后按照如下步骤构建：
     * 1. 基于Base构造Logic Meta
     * 2. 重放大于Base小于等于SP的Logic DDL
     * 3. 基于内存中最新的Logic Meta构造，构造Physical Meta
     * 4. 重放大于SP，小于等于Rollback的 Logic DDL
     * 5. 重放大于SP，小于等于Rollback的 Physical DDL
     * 6. 构建完毕
     */
    SNAPSHOT_SEMI,

    /**
     * 只有逻辑表使用基于[一致的SNAPSHOT]的事件溯源模式进行元数据构建，待逻辑表构建完成后，会在内存中得到一个Snapshot，这个Snapshot可能是一致的，也可能是不一致的
     * 物理表会基于这个Snapshot先构建出全量元数据，然后重放该该Snapshot之后的所有小于等于rollback tso的Physical DDL Event，不安全的方案，早期版本使用
     * <p>
     * 1. 基于逻辑表Schema直接创建出物理表Schema，这是一个有缺陷的模式，可能会触发数据一致性问题(是个概率事件)
     * 2. 数据一致性问题的细节可参见：
     * https://work.aone.alibaba-inc.com/issue/39018646
     * https://work.aone.alibaba-inc.com/issue/38874539
     */
    SNAPSHOT_UNSAFE,

    /**
     * 从SNAPSHOT_EXACTLY和SNAPSHOT_SEMI中随机选择，主要在实验室环境使用
     */
    RANDOM,
}
