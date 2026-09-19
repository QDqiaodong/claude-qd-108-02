SET NAMES utf8mb4;

DROP TABLE IF EXISTS calib_occ;
DROP TABLE IF EXISTS calib_batch;
DROP TABLE IF EXISTS calib_seq;
DROP TABLE IF EXISTS road_closure;
DROP TABLE IF EXISTS closure_seq;
DROP TABLE IF EXISTS equip_loan;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS booth_code_log;
DROP TABLE IF EXISTS booth;
DROP TABLE IF EXISTS hall;

CREATE TABLE hall (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  area INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hall_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  hall_id BIGINT NOT NULL,
  area INT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booth_code (code),
  KEY idx_booth_hall (hall_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 改号留痕：旧号永久停用（唯一），门卫拿旧函能对上现在的新号，
-- 旧号不能再摆新展位，同一块面积不会被新旧两套号各算一次
CREATE TABLE booth_code_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  booth_id BIGINT NOT NULL,
  old_code VARCHAR(32) NOT NULL,
  new_code VARCHAR(32) NOT NULL,
  changed_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booth_code_log_old (old_code),
  KEY idx_booth_code_log_booth (booth_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  booth_id BIGINT NOT NULL,
  booth_code VARCHAR(32) NOT NULL,
  expo_name VARCHAR(128) NOT NULL,
  tenant VARCHAR(64) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_code (code),
  KEY idx_booking_booth (booth_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equipment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  total INT NOT NULL,
  available INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_equipment_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equip_loan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  equipment_id BIGINT NOT NULL,
  booking_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  out_date DATE NOT NULL,
  back_date DATE NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_loan_equip_booking (equipment_id, booking_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE closure_seq (
  id BIGINT NOT NULL,
  seq_value BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE calib_seq (
  id BIGINT NOT NULL,
  seq_value BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE calib_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  quantity INT NOT NULL,
  expect_back_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  back_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_calib_batch_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE calib_occ (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  loan_id BIGINT NOT NULL,
  equipment_id BIGINT NOT NULL,
  booking_id BIGINT NOT NULL,
  hall_id BIGINT NOT NULL,
  expo_name VARCHAR(128) NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_occ_batch (batch_id, status),
  KEY idx_occ_loan (loan_id, status),
  KEY idx_occ_booking (booking_id, status),
  KEY idx_occ_hall (hall_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE road_closure (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  booking_id BIGINT NOT NULL,
  booth_code VARCHAR(32) NOT NULL,
  hall_id BIGINT NOT NULL,
  expo_name VARCHAR(128) NOT NULL,
  tenant VARCHAR(64) NOT NULL,
  close_date DATE NOT NULL,
  channel VARCHAR(64) NOT NULL,
  segment VARCHAR(64) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(255) NULL,
  submitted_at DATETIME NOT NULL,
  reviewed_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_closure_code (code),
  KEY idx_closure_book (booking_id, status),
  KEY idx_closure_hall_day (hall_id, close_date, channel, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO hall (code, name, area, status) VALUES
('H-01', '一号展馆', 2000, '启用'),
('H-02', '二号展馆', 1500, '启用'),
('H-03', '三号展馆', 800, '布展'),
('H-04', '四号展馆', 600, '停用');

INSERT INTO booth (code, hall_id, area, kind, status) VALUES
('A-101', 1, 200, '标准', '已租'),
('A-102', 1, 300, '特装', '空闲'),
('A-103', 1, 400, '光地', '维修'),
('B-201', 2, 150, '标准', '已租'),
('B-202', 2, 250, '标准', '已租');

INSERT INTO booking (code, booth_id, booth_code, expo_name, tenant, start_date, end_date, status) VALUES
('BK-0901', 1, 'A-101', '秋季家居展', '红星家居', '2026-09-10', '2026-09-20', '展出中'),
('BK-0902', 4, 'B-201', '珠宝首饰展', '宝盛珠宝', '2026-09-15', '2026-09-25', '待布展'),
('BK-0904', 5, 'B-202', '金秋婚博会', '良缘会展', '2026-09-19', '2026-09-22', '待布展'),
('BK-0903', 2, 'A-102', '茶文化博览会', '茗香茶业', '2026-09-01', '2026-09-05', '已结束');

INSERT INTO equipment (code, name, kind, total, available) VALUES
('EQ-001', '铝合金桁架', '桁架', 200, 150),
('EQ-002', '标准展板', '展板', 500, 500),
('EQ-003', '洽谈桌椅（套）', '桌椅', 80, 60),
('EQ-004', 'LED 射灯', '灯具', 300, 20);

INSERT INTO equip_loan (equipment_id, booking_id, quantity, out_date, back_date, status) VALUES
(1, 1, 50, '2026-09-09', NULL, '借用中'),
(3, 1, 20, '2026-09-09', NULL, '借用中'),
(2, 3, 100, '2026-08-30', '2026-09-06', '已归还'),
(4, 1, 120, '2026-09-09', NULL, '借用中'),
(4, 2, 60, '2026-09-14', NULL, '借用中'),
(4, 3, 40, '2026-09-18', NULL, '借用中'),
(4, 4, 10, '2026-08-30', NULL, '借用中');

INSERT INTO closure_seq (id, seq_value) VALUES (1, 2);

INSERT INTO calib_seq (id, seq_value) VALUES (1, 1);

-- 库房便签搬上台账的第一批：LED 射灯 50 盏送厂校准，点名占用三条借用行；
-- 茶文化博览会那条排期已结束，占用行已是「待归还厂」，可借不加回
INSERT INTO calib_batch (code, kind, quantity, expect_back_date, status, created_at, back_at)
VALUES
('JC-0001', '灯具', 50, '2026-09-28', '校准中', '2026-09-17 10:20:00', NULL);

INSERT INTO calib_occ (batch_id, loan_id, equipment_id, booking_id, hall_id, expo_name, quantity, status) VALUES
(1, 4, 4, 1, 1, '秋季家居展', 25, '校准中'),
(1, 5, 4, 2, 2, '珠宝首饰展', 15, '校准中'),
(1, 7, 4, 4, 1, '茶文化博览会', 10, '待归还厂');

INSERT INTO road_closure
  (code, booking_id, booth_code, hall_id, expo_name, tenant, close_date, channel, segment,
   start_time, end_time, status, reason, submitted_at, reviewed_at)
VALUES
  ('RD-0001', 2, 'B-201', 2, '珠宝首饰展', '宝盛珠宝', '2026-09-19', '北卸货通道', '东段（3号门段）',
   '08:00:00', '11:00:00', '已批准', NULL, '2026-09-15 09:12:00', '2026-09-15 10:05:00'),
  ('RD-0002', 3, 'B-202', 2, '金秋婚博会', '良缘会展', '2026-09-19', '北卸货通道', '西段（1号门段）',
   '13:30:00', '16:30:00', '待审', NULL, '2026-09-16 14:40:00', NULL);
