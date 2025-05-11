# aurorainkv
***
我的本科毕业设计，实现一个基于RocksDB的分布式KV存储，主要实现模块为Raft、Multi-Raft、Percolator分布式事务

raft部分实现参考[Sakameeee/MIT6.824-Java-2021](https://github.com/Sakameeee/MIT6.824-Java-2021)，我在此基础上将存储引擎改为RocksDB，在raft的基础上改成multi-raft，并实现基于percolator的分布式事务，同时加入了UI控制台。

仅供学习参考~

raft相关工程实现的资料比较多，这里提一下分布式事务的实现：

### percolator事务模型基础

系统实现了SI快照隔离级别，系统不再是单纯存储某个Key，而是会以快照的方式，将Key和时间戳一起存储，这种方式被称为MVCC（多版本并发控制）。

系统的事务设计遵循Percolator事务模型，它是一个两阶段提交协议（2PC）。一个事务是一系列写操作的序列。Percolator模型在BigTable的基础上，提供了Snapshot和Isolation语义的分布式事务。系统实现分布式事务主要基于三个组件：Client、TSO、ShardKV事务模块。其中Client是事务的发起者和协调者，包含了传统2PC中的协调者角色。TSO组件是一个为分布式服务器提供精确的、严格单调递增的时间戳服务。ShardKV中的Transaction模块控制与事务存储的信息有关的模块。Transaction模块借助RocksDB的列簇能力，使用不同列簇来存储不同的事务信息类型。系统中使用了三个列簇：default列簇、lock列簇和write列簇。default列簇存储用户原来的value，假设时间戳为ts，则存储在default列簇里面的值为value:ts。lock列簇存储了分布式事务中锁的信息，这个列簇是在分布式事务预写阶段写入的。write列簇写入代表了事务提交的状态，在commit阶段写入。

Percolator使用lock和write两个列簇来表达事务的状态。Percolator的分布式写事务是由两阶段提交2PC实现的，一个写事务包含多个写操作序列，事务开启时，Client会从TSO组件获取一个时间戳StartTS作为事务作为事务开始的时间。在提交之前，所有的写操作都会缓存在内存里。提交过程包含了预写prewrite阶段和commit阶段，prewrite阶段类似于2PC中的第一阶段提交，percolator算法会从事务的写操作序列中选取一个Key作为primary键，prewrite阶段会优先处理primary键。同样地，commit阶段也会优先处理primary键。Precolator事务模型会通过primary键的lock、write列簇数据来推测事务的执行状态。

![image](https://github.com/user-attachments/assets/f92441cf-fcce-47da-867d-ac3aa396a946)

### 两阶段提交
本节会详细讲解分布式事务两阶段提交的实现。对于每一个事务，系统将事务封装为一个Java对象TwoPhaseCommitter，该对象会负责该事务的两阶段提交的执行过程。该对象封装了事务所用的变量，如TSO组件、primary主键、事务Id、写操作序列列表entries以及事务的一些状态信息。TwoCommitter对象还封装了prewrite、commit、rollback、execute等方法，控制着事务的执行过程。

TwoPhaseCommitter的prewrite方法协调完成了分布式事务的预写阶段。首先使用了Java异步编排线程池CompletionService，对于用户事务的每一个写操作，通过Client路由到操作的Key所在的节点中进行kvPrewrite RPC调用。kvPrewrite由ShardKV的Transaction层实现，具体逻辑如下：

（1）首先通过RocksDB层接口得到的迭代器RocksDBReader，并创建MvccTxn对象来进行操作事务相关的字段。MvccTxn类提供了对指定RocksDB的default列簇、lock列簇、write列簇的键值对的读、写操作。
（2）通过MvccTxn接口查询指定Key是否有在startTs之后的Write列的记录，如果存在则发生了写写冲突，将此冲突结果返回。TwoPhaseCommitter收到该写写冲突后，会调用rollback方法进行回滚，且停止CompletionService中其它线程的RPC调用的执行。
（3）通过MvccTxn查找是否在lock列簇中存在Key记录，如果存在，这里会有很多可能性。可能有其它事务正在进行提交操作，可能其它事务crash了，lock列没有删除。这里的具体处理逻辑会在故障恢复小节详细讲到。
（4）如果不存在冲突，则会对这个写操作锁定，即在lock列簇写入这个Key的锁数据，并带上startTS时间戳、指向的Primary键和一个超时时间。同时要在default列簇中写入Value数据，同时也要写入startTs时间戳。要写入RocksDB的内容将会加入WriteBatch中，由RocksDB通过事务执行。

![image](https://github.com/user-attachments/assets/dd5ce854-4951-4dc2-a913-0b69cad95260)

KvPrewrite方法中，通过RocksDBReader查询write和lock列簇的数据并处理时，需要保证write列簇和lock列簇在这区间不会被修改。系统中引入了Latches类，可以对某个Key进行上锁。KvPrewrite方法在开始查询和处理write和lock列簇数据到完成RocksDB的持久化之前，需要通过Latches对要处理的Key进行上锁。通过将锁的粒度控制在Key，尽量避免了阻塞对系统性能的影响。

TwoPhaseCommitter对所有的Key都完成KvPrewrite阶段后，即完成了对要提交事务写序列的资源锁定，下一步开始执行commit阶段。TwoPhaseCommitter的commit方法中，首先对primary Key进行提交，通过Client路由到指定节点，调用RPC方法kvCommit。KvCommit的逻辑如下：

（1）首先通过RocksDB层接口得到的迭代器RocksDBReader，并创建MvccTxn对象来进行操作事务相关的字段。使用Latches对要处理的Key上锁。
（2）通过MvccTxn查找对应Key在lock列簇的锁数据，如果不存在则会直接返回，TwoPhaseCommitter会将事务回滚。
（3）如果对应Key上存在lock锁资源，则向WriteBatch中加入写write操作和删除lock操作，write操作同时也要标注其Primary键。并通过Raft提交给RocksDB的事务执行接口，以单条事务的方式执行这个WriteBatch。
Primary Key提交成功后，客户端可以直接返回给用户提交成功。该事务的Secondary键会通过线程池异步地调用kvCommit来执行提交动作。
TwoPhaseCommitter提供了回滚操作的接口，该操作会对事务操作序列的每一个Key开启单独的线程，使用Client调用RPC接口kvBatchRollback。KvBatchRollback的逻辑如下：

（1）首先通过RocksDB层接口得到的迭代器RocksDBReader，并创建MvccTxn对象来进行操作事务相关的字段。使用Latches对要处理的Key上锁。
（2）找到当前事务对Key的最近Write记录，如果存在则说明已经回滚或者提交了（Write记录的kind决定事务状态是回滚还是提交）。
（3）查找是否存在Key对应的冲突的lock，如果存在则进行roll-forward操作，即删除default列簇的value，并且删除lock列簇的锁数据，写入kind为回滚的write。不存在则直接写入write。以上操作都是通过WriteBatch进行原子执行的。

![image](https://github.com/user-attachments/assets/7101f76b-5dae-464d-aec0-4e06a9c99b4c)

### 冲突和故障恢复
一个事务是否执行成功，只取决于Commit Point。一旦事务的Commit Point确定，所有的写操作都最终必须确定且必须一致地接受。在传统的2PC中，事务的状态是根据协调者可以直接得到的，而Percolator模型需要通过default、lock、write列簇上的事务数据来确定事务状态。本节主要介绍系统如何根据某个事务存储在这三个列簇上的数据来得到事务的提交状态。

在事务A执行prewrite阶段时，可能会出现lock冲突。如果出现其它事务B的lock与事务A要写的lock发生冲突，可能是以下两种情况：（1）事务B也正在执行，且两个事务的写操作序列之间发生冲突。（2）事务B已发生crash，写入的lock残留未被清理。系统通过事务B的primary key的状态来确定事务的状态。

具体地，若事务在prewrite阶段执行过程中发生lock冲突，TwoPhaseCommitter会调用RPC方法checkTxnStatus来确定事务目前的状态。checkTxnStatus方法会查找primary key的lock和write列簇数据，有以下几种可能：
（1）write存在，说明该事务已经提交或者回滚。根据write的kind类型可以确定是提交还是回滚。如果write存在将直接返回，不会去判断（2）和（3）。
（2）lock存在，但是Ttl还未超时，此时无法判断事务B是否crash。针对这种情况，TwoPhaseCommitter会等待lock剩余超时时间后重新尝试调用该接口来判断事务状态。
（3）lock存在，且Ttl超时，这时会视此事务发生crash，会直接进行roll-forward操作，将value和lock列簇的数据删除。这里有可能将正常执行的事务误删，因此TwoPhaseCommitter在提交时会确定primary lock是否存在，如果不存在则会直接回滚。
（4）lock和write都不存在，TwoPhaseCommitter认为事务已经回滚，则会对当前Key执行回滚操作。

![image](https://github.com/user-attachments/assets/b0405728-cba8-4984-919c-84b29a4a8d9f)

对于上面的（1）和（4）情况，TwoPhaseCommitter认为需要该事务已经提交或者回滚，它会通过Client调用RPC方法kvResolveLock来获取该事务所有的含有Lock的Key，并做统一的Commit或者Rollback处理。

这里给出UI控制台的操作：

### 数据冗余展示

本节展示了系统对数据冗余的实现。首先系统启动一个ShardMaster集群，然后启动一个ShardKV集群，Gid设置为1。分片管理中设置分片全部给Gid = 1集群。
然后，系统通过控制台->kv管理向Gid = 1集群中加入了两个键值对，Key分别为sabc和abc。

![image](https://github.com/user-attachments/assets/eb624245-520e-4c12-9d19-ca3ccfd4b8ae)
 

接下来打开节点管理->ShardKV面板，当前Gid = 1的集群有三个节点。通过点击查看键值对按钮可以看RocksDB里面有哪些数据。可以看到，三个节点，无论Leader还是Follower，都有sabc和abc两个键值对，证明主节点将日志复制到了集群中的从节点，完成了数据冗余。

 ![image](https://github.com/user-attachments/assets/44493267-a9b6-4fef-b7e2-caa4c637d1bd)

### 数据一致性验证展示

本节展示系统的数据一致性测试。首先系统启动ShardMaster集群和Gid = 1的ShardKV集群。分片管理设置将所有分片交由Gid = 1管理。
打开可视化验证->单client验证。添加50个操作数，点击执行操作。系统会将随机生成的操作通过请求发送给后端去执行。ShardServer会使用一个Client去执行这些请求，并将所有操作执行结果一并返回给后端。同时，前端在内存中维护一个数据结构去完成这些操作，并拿后端接收到结果与前端内存中的操作结果做对比，将验证结果在前端展示。

 ![image](https://github.com/user-attachments/assets/7ab7c4a2-62a1-4a47-a0cd-a947cc45cd0f)

进入可视化验证->并发验证界面，点击执行测试按钮。后端会自动生成三轮测试，且创建三个Client，这三个Client可能操作到相同的Key。三个Client并发地执行随机生成的Put、Get命令，让服务端执行。由于多个Client的请求会因为网络等不确定性因素，所以无法从Client确定服务端处理的顺序，操作被处理的顺序是由Server端的Leader节点Raft日志顺序决定。Server端会对操作的执行结果进行验证，并将执行结果返回给前端。

![image](https://github.com/user-attachments/assets/0f661be2-02a5-48fb-9dce-3e9978e0e9e9)

### 容错和故障恢复展示

本节会展示系统的容错和故障恢复。首先系统启动一个ShardMaster集群，并开启一个ShardKV集群，将所有分片分配给Gid = 1的集群。首先，在节点管理->ShardKV节点面板，看到Gid = 1的集群运行了三个节点，其中有一个是Leader节点。通过控制台->kv管理向集群中加入两个键值对，Key分别是abc和sabc。此时，点击Leader2节点的断开按钮，Leader2节点与其它节点的Raft RPC通信会断开。
 
![image](https://github.com/user-attachments/assets/0a36cfe6-217a-4a28-ac41-492950464478)

断开Leader的Raft RPC通信后，重新刷新节点，发现节点3变成了新的Leader节点，继续为系统提供服务。用户通过kv管理重新向系统加入gabc，发现节点1和节点3完成了日志的复制，且系统还能正常提供服务，这展示了系统的容错能力。
 
![image](https://github.com/user-attachments/assets/85c69af8-147a-48c7-809c-fb3178bffc3a)

现在点击节点2的连接按钮，让旧Leader节点重新建立与集群的RPC通信。
 此时刷新节点，发现节点2变成Follower节点，且gabc键值对已经成功复制到节点2上，如图证明了系统具有的故障恢复能力。
 
![image](https://github.com/user-attachments/assets/a62531b1-21be-4696-86ef-2d27b16b1d36)

### 数据分片展示

本节展示系统的数据分片功能。系统首先启动一个ShardMaster集群，然后进入控制台->分片管理面板，加入Gid = 1的集群，之后再加入Gid = 2的集群。分片会通过负载均衡均分所有分片给Gid = 1的集群和Gid = 2的集群。
 
![image](https://github.com/user-attachments/assets/faf21e1a-a076-47bb-8062-7cdb1f4a5fcb)

启动Gid = 1和Gid = 2的ShardKV集群。
 
![image](https://github.com/user-attachments/assets/27570104-658b-44e6-914f-afa205e32652)

通过kv管理向系统中加入Key分别为sabc、abc、gabc三个键值对。其中abc、gabc根据分片规则属于Gid = 2的集群存储，sabc根据分片规则属于Gid = 1的集群存储。
 
![image](https://github.com/user-attachments/assets/d3b5892c-3d48-47e4-bcc0-a8140d9663d2)

现在打开控制台->分片管理，将分片6移动到Gid = 2，系统的分片管理如图所示。
 
![image](https://github.com/user-attachments/assets/31663927-37f1-4129-8d49-1964046219db)

重新进入节点管理->ShardKV节点，发现分片gabc从Gid = 2集群移动到了Gid = 1集群中，验证了系统的数据迁移功能，效果如图所示。
 
![image](https://github.com/user-attachments/assets/b7bd2665-19aa-4c81-90c9-beaf713f50b1)

### Raft选主展示

本节通过前端可视化组件展示了系统的Raft选主算法。系统将ShardMaster节点的Raft算法日志通过WebSocket技术实时传送给前端，前端不仅会对日志进行解析，还会通过3d库展示每个任期的节点状态图。
如图所示，该图展示了三个节点在任期1内的状态，由于三个节点几乎同时发起选举，会因为互相竞争锁导致并发冲突，所以任期1内没有选出Leader节点，三个节点的状态都是Candidate节点。
 
![image](https://github.com/user-attachments/assets/d29b24b6-2fb2-4678-9348-956de8f199bc)

选择框选择任期2，可以看到在该任期内，节点1和节点2分别投给节点0一票，节点0选举成功为Leader节点，如图所示。
 
![image](https://github.com/user-attachments/assets/0f03f4cb-9429-449c-9ff9-c68a48db2749)

### 分布式事务展示

打开可视化验证->分布式事务面板，进行分布式事务测试。其中系统支持crash、execute2PC。其中crash模拟事务crash，在prewrite阶段完成后会直接执行结束，不会执行commit阶段。
首先我新增了一个crash事务、又加了两个execute2PC事务，其中crash的事务分别与两个execute2PC事务存在冲突。点击执行事务，前端收到的执行结果如图所示。
 
![image](https://github.com/user-attachments/assets/eaec1f7e-8334-4095-b2ad-1bc1c0ba90ca)

事务1在crash之后，事务2和事务3都在prewrite阶段执行事务时发现Lock冲突，且第一次检测Primary Lock时会发现Ttl还未超时，此时不确定事务的状态，等待一定时间进行重试。重试后发现Primary Lock超时，确定事务出现crash，在checkTxnStatus时直接执行roll-forward。于是两个事务会在resolveLocks中得到def需要回滚，并执行回滚操作，然后继续尝试prewrite操作，发现执行成功。最后执行commit完成提交。上面的过程如图日志所示。
 
![image](https://github.com/user-attachments/assets/e7a5dae0-ff5b-4c25-9903-6bf3eff17177)

下面测试没有crash，只有execute2PC操作，即正常执行两阶段提交。我设置了三个execute2PC事务，其中事务1和事务2存在Key冲突。点击执行事务，得到的执行界面如图所示。
 
![image](https://github.com/user-attachments/assets/c0020337-584e-4f40-967a-5d82a6eb42d1)

传给前端的日志如图所示。可以看到，事务1因为先写入lock，所以不会检测到冲突，首先提交成功。事务3不会出现冲突，正常提交成功。事务2会检测到Lock冲突，调用checkTxns检查事务1的Primary Lock时发现事务1已经提交，此时会重新prewrite，此时发现事务1的write记录，即事务1在事务2开始后进行了提交，事务2此时会进行回滚。回滚后，事务2重新进行两阶段提交，新的时间戳不会产生冲突，事务2提交成功。

![image](https://github.com/user-attachments/assets/9caabd74-8091-4ce3-8241-5e04d673a8fc)
