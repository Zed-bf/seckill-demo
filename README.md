# 高并发秒杀系统 (Seckill Demo)

基于 Spring Boot 3.x 的高并发秒杀（抢购）系统，采用 **Redis 预扣减 + RocketMQ 异步削峰 + MySQL 乐观锁** 的三层架构设计，有效应对瞬时高并发场景，防止超卖和数据库雪崩。

## 📋 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [API 接口](#api-接口)
- [数据库设计](#数据库设计)
- [核心技术方案](#核心技术方案)
- [配置说明](#配置说明)
- [测试建议](#测试建议)
- [常见问题](#常见问题)

---

## 项目简介

本项目是一个典型的高并发秒杀系统实现，模拟电商场景中商品限时限量抢购的业务场景。通过多层防护机制，在保证数据一致性的前提下，最大化系统吞吐量和响应速度。

### 业务场景

- 商品库存有限（如 100 台 iPhone）
- 大量用户同时发起购买请求（如 10000+ QPS）
- 需要在毫秒级响应时间内完成库存扣减和订单创建
- 未支付订单需要自动取消并回滚库存

---

## 技术栈

### 后端框架
- **Java 17** - LTS 版本
- **Spring Boot 3.2.5** - 应用框架
- **MyBatis-Plus 3.5.6** - ORM 框架，简化数据库操作

### 数据存储
- **MySQL 8.0+** - 关系型数据库，持久化订单和商品信息
- **Redis 7.0+** - 内存数据库，用于库存预扣减和防重

### 消息队列
- **Apache RocketMQ 2.3.3** - 分布式消息中间件，实现异步削峰和延迟消息

### 工具库
- **Lombok** - 简化 Java 代码
- **Hutool 5.8.27** - Java 工具类库
- **Spring Validation** - 参数校验

### 构建工具
- **Maven 3.6+**

---

## 核心特性

✅ **Redis 原子性库存预扣减**  
使用 Lua 脚本保证库存扣减的原子性，避免超卖问题，QPS 可达数万级别

✅ **RocketMQ 异步削峰**  
将订单创建和库存扣减解耦，通过消息队列缓冲瞬时流量高峰

✅ **延迟消息超时取消**  
利用 RocketMQ 延迟消息实现订单超时自动取消（如 30 分钟未支付）

✅ **MySQL 乐观锁**  
支付阶段使用版本号机制扣减库存，保证最终一致性

✅ **防重复提交**  
基于 Redis 实现用户级别的幂等性控制，防止同一用户重复下单

✅ **库存回滚机制**  
订单取消时自动回滚 Redis 和 MySQL 库存，保证数据一致性

---

## 系统架构

### 整体流程图

```
用户请求 
   ↓
Controller 层（参数校验）
   ↓
Service 层
   ↓
┌─────────────────────────────────────┐
│  第一层：Redis 预扣减（Lua 脚本）    │ ← 抗住大部分流量
│  - 检查库存是否充足                   │
│  - 原子性扣减库存                     │
│  - 失败直接返回"已售罄"               │
└─────────────────────────────────────┘
   ↓ 成功
┌─────────────────────────────────────┐
│  第二层：创建订单（MySQL）            │ ← 异步化处理
│  - 生成订单号                         │
│  - 插入订单记录（状态 UNPAID）        │
│  - 发送 RocketMQ 延迟消息             │
└─────────────────────────────────────┘
   ↓
┌─────────────────────────────────────┐
│  第三层：支付确认（MySQL 乐观锁）     │ ← 最终一致性
│  - 用户发起支付                       │
│  - UPDATE t_product SET stock=...    │
│    WHERE id=? AND version=?          │
│  - 更新订单状态为 PAID                │
└─────────────────────────────────────┘
   ↓
┌─────────────────────────────────────┐
│  延迟消息消费者（超时取消）           │
│  - 30 分钟后检查订单状态              │
│  - 若仍为 UNPAID，取消订单            │
│  - 回滚 Redis + MySQL 库存           │
└─────────────────────────────────────┘
```

### 分层防护策略

| 层级 | 技术方案 | 作用 | 性能 |
|------|---------|------|------|
| 第一层 | Redis + Lua | 拦截 90%+ 无效请求 | ~50,000 QPS |
| 第二层 | RocketMQ | 削峰填谷，异步处理 | ~10,000 QPS |
| 第三层 | MySQL 乐观锁 | 保证最终一致性 | ~1,000 QPS |

---

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7.0+
- Apache RocketMQ 4.9+（可选，可关闭 MQ 开关）

### 1. 克隆项目

```bash
git clone <repository-url>
cd seckill-demo
```

### 2. 初始化数据库

执行 `sql/schema.sql` 脚本创建数据库和表：

```bash
mysql -u root -p < sql/schema.sql
```

该脚本会：
- 创建数据库 `seckill_db`
- 创建商品表 `t_product` 和订单表 `t_order`
- 初始化一个测试商品（iPhone 16 Pro Max，库存 100）

### 3. 配置环境变量

修改 `src/main/resources/application.yml` 中的以下配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seckill_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # 修改为你的 MySQL 用户名
    password: your_password # 修改为你的 MySQL 密码

  data:
    redis:
      host: 127.0.0.1       # 修改为你的 Redis 地址
      port: 6379
      password: your_redis_password # 修改为你的 Redis 密码（如无密码则留空）

rocketmq:
  name-server: 127.0.0.1:9876 # 修改为你的 RocketMQ NameServer 地址
```

### 4. 启动 RocketMQ（可选）

如果需要使用延迟消息功能，请先启动 RocketMQ：

```bash
# 启动 NameServer
nohup sh mqnamesrv &

# 启动 Broker
nohup sh mqbroker -n localhost:9876 &
```

> **提示**：如果暂时不安装 RocketMQ，可以在 `application.yml` 中设置 `seckill.mq.enabled: false`，系统将降级为同步处理模式。

### 5. 编译并运行

```bash
# 编译项目
mvn clean package -DskipTests

# 运行应用
java -jar target/seckill-demo-1.0.0-SNAPSHOT.jar
```

或者使用 IDE 直接运行 `SeckillApplication.java`

### 6. 验证服务

服务启动成功后，访问：

```bash
curl http://localhost:8080/api/seckill/place-order \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "userId": "user001",
    "quantity": 1
  }'
```

预期响应：

```json
{
  "success": true,
  "message": "下单成功",
  "orderId": 1,
  "orderNo": "SK20260620001234567"
}
```

---

## API 接口

### 1. 下单接口

**请求**

```http
POST /api/seckill/place-order
Content-Type: application/json

{
  "productId": 1,      // 商品ID（必填）
  "userId": "user001", // 用户ID（必填）
  "quantity": 1        // 购买数量（必填，必须 > 0）
}
```

**响应**

```json
{
  "success": true,
  "message": "下单成功",
  "orderId": 1,
  "orderNo": "SK20260620001234567"
}
```

**错误响应**

```json
{
  "success": false,
  "message": "库存不足"
}
```

或

```json
{
  "success": false,
  "message": "您已购买过该商品，请勿重复下单"
}
```

---

### 2. 支付接口

**请求**

```http
POST /api/seckill/pay?orderId=1
```

**响应**

```json
{
  "success": true,
  "message": "支付成功"
}
```

**错误响应**

```json
{
  "success": false,
  "message": "订单不存在或已支付"
}
```

---

## 数据库设计

### 商品表 (t_product)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| product_name | VARCHAR(100) | 商品名称 |
| total_stock | INT | 总库存（仅作参考） |
| stock | INT | 剩余库存（核心字段） |
| price | DECIMAL(10,2) | 单价 |
| version | INT | 乐观锁版本号 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**：PRIMARY KEY (`id`)

---

### 订单表 (t_order)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| order_no | VARCHAR(64) | 订单编号（唯一） |
| product_id | BIGINT | 商品ID |
| user_id | VARCHAR(64) | 用户ID |
| quantity | INT | 购买数量 |
| total_amount | DECIMAL(10,2) | 订单金额 |
| status | VARCHAR(20) | 订单状态：UNPAID/PAID/CANCELED |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**索引**：
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_order_no` (`order_no`)
- KEY `idx_product_id` (`product_id`)
- KEY `idx_user_id` (`user_id`)
- KEY `idx_status_create_time` (`status`, `create_time`)

---

## 核心技术方案

### 1. Redis 库存预扣减（Lua 脚本）

**文件位置**：`src/main/resources/lua/deduct_stock.lua`

```lua
local key = KEYS[1]
local amount = tonumber(ARGV[1])

-- 读取当前库存
local stock = tonumber(redis.call('GET', key) or '0')

if stock <= 0 or stock < amount then
    return -1
end

-- 原子性扣减
local remaining = redis.call('DECRBY', key, amount)
return remaining
```

**优势**：
- ✅ 原子性执行，避免并发问题
- ✅ 减少网络往返次数
- ✅ 高性能，单机可达 5万+ QPS

---

### 2. RocketMQ 延迟消息

**应用场景**：订单超时自动取消

```java
// 发送延迟消息（延迟级别 16 = 30 分钟）
Message<String> message = MessageBuilder.withPayload(orderNo)
    .setHeader(RocketMQHeaders.TOPIC, MqConstants.ORDER_TIMEOUT_TOPIC)
    .build();

rocketMQTemplate.syncSend(message, 30000, 16); // 16 对应 30m
```

**延迟级别对照表**：

| 级别 | 延迟时间 |
|------|---------|
| 1 | 1s |
| 2 | 5s |
| 3 | 10s |
| ... | ... |
| 16 | 30m |
| 17 | 1h |
| 18 | 2h |

---

### 3. MySQL 乐观锁

**支付时扣减库存**：

```sql
UPDATE t_product 
SET stock = stock - #{quantity}, 
    version = version + 1 
WHERE id = #{productId} 
  AND version = #{version} 
  AND stock >= #{quantity}
```

**工作原理**：
1. 查询商品时获取当前 `version`
2. 更新时携带 `version` 作为条件
3. 如果 `version` 已被其他事务修改，则更新失败（影响行数为 0）
4. 重试或返回失败

---

### 4. 库存回滚机制

**触发场景**：
- 订单超时未支付
- 支付失败
- 用户主动取消

**回滚流程**：

```java
@Transactional
public void cancelOrder(Long orderId) {
    // 1. 查询订单
    Order order = orderMapper.selectById(orderId);
    
    // 2. 回滚 Redis 库存
    String stockKey = "seckill:stock:" + order.getProductId();
    redisTemplate.opsForValue().increment(stockKey, order.getQuantity());
    
    // 3. 回滚 MySQL 库存
    productMapper.increaseStock(order.getProductId(), order.getQuantity());
    
    // 4. 更新订单状态为 CANCELED
    order.setStatus("CANCELED");
    orderMapper.updateById(order);
}
```

---

## 配置说明

### 关键配置项

```yaml
# 数据库连接池配置
spring:
  datasource:
    hikari:
      minimum-idle: 10          # 最小空闲连接数
      maximum-pool-size: 50     # 最大连接数（根据并发量调整）
      connection-timeout: 30000 # 连接超时时间（毫秒）

# Redis 连接池配置
  data:
    redis:
      lettuce:
        pool:
          max-active: 50        # 最大活跃连接数
          max-idle: 20          # 最大空闲连接数
          min-idle: 10          # 最小空闲连接数

# RocketMQ 配置
rocketmq:
  producer:
    send-message-timeout: 3000       # 发送超时时间（毫秒）
    retry-times-when-send-failed: 2  # 发送失败重试次数

# 业务开关
seckill:
  mq:
    enabled: true  # 是否启用 RocketMQ（无 MQ 环境时设为 false）
```

### 性能调优建议

| 配置项 | 低并发场景 | 高并发场景 |
|--------|-----------|-----------|
| Hikari maximum-pool-size | 20 | 50-100 |
| Redis max-active | 20 | 50-100 |
| RocketMQ retry-times | 1 | 2-3 |

---

## 测试建议

### 1. 单接口测试

使用 Postman 或 curl 测试单个接口：

```bash
# 下单
curl -X POST http://localhost:8080/api/seckill/place-order \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"userId":"user001","quantity":1}'

# 支付
curl -X POST http://localhost:8080/api/seckill/pay?orderId=1
```

---

### 2. 并发测试（JMeter）

**测试场景**：1000 个线程同时抢购 100 件商品

**JMeter 配置**：
- Thread Group: 1000 threads
- Ramp-Up Period: 1 second
- Loop Count: 1
- HTTP Request: POST /api/seckill/place-order

**预期结果**：
- 成功订单数：100（等于库存）
- 失败订单数：900（库存不足）
- 无超卖现象

---

### 3. 压力测试（wrk）

```bash
# 安装 wrk
brew install wrk  # macOS
# 或
sudo apt-get install wrk  # Ubuntu

# 压测命令
wrk -t12 -c400 -d30s -s post.lua http://localhost:8080/api/seckill/place-order
```

**post.lua 示例**：

```lua
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"productId":1,"userId":"user"..tostring(math.random(10000)),"quantity":1}'
```

---

### 4. 数据一致性验证

```sql
-- 验证库存是否正确扣减
SELECT stock FROM t_product WHERE id = 1;

-- 验证订单数量与库存扣减量是否一致
SELECT COUNT(*) as order_count 
FROM t_order 
WHERE product_id = 1 AND status = 'PAID';

-- 验证是否有超卖
SELECT stock FROM t_product WHERE id = 1;
-- stock 应该 >= 0
```



## 项目结构

```
seckill-demo/
├── sql/                          # 数据库脚本
│   └── schema.sql               # 建表脚本
├── src/main/
│   ├── java/com/example/seckill/
│   │   ├── config/              # 配置类
│   │   │   ├── DataInitRunner.java      # 数据初始化
│   │   │   ├── GlobalExceptionHandler.java  # 全局异常处理
│   │   │   ├── MqConstants.java         # MQ 常量定义
│   │   │   ├── MyMetaObjectHandler.java # MyBatis-Plus 元数据处理
│   │   │   └── RedisConfig.java         # Redis 配置
│   │   ├── controller/          # 控制器层
│   │   │   └── SeckillController.java   # 秒杀接口
│   │   ├── dto/                 # 数据传输对象
│   │   │   └── SeckillRequest.java      # 下单请求 DTO
│   │   ├── entity/              # 实体类
│   │   │   ├── Order.java               # 订单实体
│   │   │   └── Product.java             # 商品实体
│   │   ├── mapper/              # 数据访问层
│   │   │   ├── OrderMapper.java         # 订单 Mapper
│   │   │   └── ProductMapper.java       # 商品 Mapper
│   │   ├── mq/                  # 消息队列
│   │   │   ├── OrderTimeoutConsumer.java  # 超时订单消费者
│   │   │   └── OrderTimeoutProducer.java  # 延迟消息生产者
│   │   ├── service/             # 服务层
│   │   │   ├── SeckillService.java      # 服务接口
│   │   │   └── impl/
│   │   │       └── SeckillServiceImpl.java  # 服务实现
│   │   └── SeckillApplication.java  # 启动类
│   └── resources/
│       ├── lua/                 # Lua 脚本
│       │   ├── deduct_stock.lua         # 扣减库存脚本
│       │   └── rollback_stock.lua       # 回滚库存脚本
│       └── application.yml      # 应用配置
├── target/                      # 编译输出目录
├── pom.xml                      # Maven 配置
└── .gitignore                   # Git 忽略配置
```

---

## 性能优化建议

### 1. Redis 优化
- 使用 Redis Cluster 提升吞吐量
- 开启 AOF 持久化保证数据可靠性
- 调整 `maxmemory-policy` 为 `allkeys-lru`

### 2. MySQL 优化
- 为高频查询字段添加索引
- 使用读写分离（主从复制）
- 开启慢查询日志，优化 SQL

### 3. 应用层优化
- 启用 Gzip 压缩响应数据
- 使用连接池复用数据库和 Redis 连接
- 异步化非核心业务流程

### 4. 架构优化
- 引入 CDN 缓存静态资源
- 使用 Nginx 负载均衡
- 部署多实例实现水平扩展

---

## 监控与告警

### 关键指标

| 指标 | 阈值 | 说明 |
|------|------|------|
| Redis QPS | > 10,000 | 监控 Redis 负载 |
| MySQL 连接数 | > 80% | 连接池使用率 |
| 订单成功率 | < 95% | 业务异常告警 |
| 平均响应时间 | > 500ms | 性能下降告警 |
| RocketMQ 积压量 | > 10,000 | 消费能力不足 |

### 推荐工具
- **Prometheus + Grafana**：系统监控和可视化
- **SkyWalking**：分布式链路追踪
- **ELK Stack**：日志收集和分析

---
## 参考资料

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [RocketMQ 官方文档](https://rocketmq.apache.org/)
- [Redis 官方文档](https://redis.io/documentation)
- 《高并发系统设计》- 极客时间

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
