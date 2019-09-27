--
-- Copyright 2018 herd-mdl contributors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- MySQL dump 10.13  Distrib 5.5.59, for Linux (x86_64)
--
-- Host: metastor Database: metastor
-- ------------------------------------------------------
-- Server version	5.6.29-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `DM_NOTIFICATION`
--

DROP TABLE IF EXISTS `DM_NOTIFICATION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DM_NOTIFICATION` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `NAMESPACE` varchar(50) DEFAULT NULL,
  `OBJECT_DEF_NAME` varchar(100) NOT NULL,
  `USAGE_CODE` varchar(50) DEFAULT NULL,
  `FILE_TYPE` varchar(50) DEFAULT NULL,
  `DATE_CREATED` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `PARTITION_KEY` varchar(100) DEFAULT NULL,
  `PARTITION_VALUES` varchar(2500) DEFAULT NULL,
  `WF_TYPE` int(11) NOT NULL,
  `CORRELATION_DATA` text,
  `EXECUTION_ID` varchar(36) NOT NULL,
  `CLUSTER_NAME` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `ID_UNIQUE` (`ID`),
  KEY `IDX_EXECUTION_ID` (`EXECUTION_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=293105 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`MS_Hive_0_13`@`%`*/ /*!50003 TRIGGER `DM_NOTIFICATION_BEFORE_DELETE` BEFORE DELETE ON `DM_NOTIFICATION` FOR EACH ROW
BEGIN

  DELETE FROM METASTOR_PROCESSING_LOG WHERE NOTIFICATION_ID = OLD.ID;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`MS_Hive_0_13`@`%`*/ /*!50003 TRIGGER `DM_NOTIFICATION_AFTER_DELETE` AFTER DELETE ON `DM_NOTIFICATION` FOR EACH ROW
BEGIN

  INSERT INTO DM_NOTIFICATION_HIST (ID, NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, DATE_CREATED, PARTITION_KEY, PARTITION_VALUES, WF_TYPE
  , CORRELATION_DATA, EXECUTION_ID, CLUSTER_NAME)
  VALUES (OLD.ID, OLD.NAMESPACE, OLD.OBJECT_DEF_NAME, OLD.USAGE_CODE, OLD.FILE_TYPE, OLD.DATE_CREATED, OLD.PARTITION_KEY, OLD.PARTITION_VALUES, OLD.WF_TYPE
  , OLD.CORRELATION_DATA, OLD.EXECUTION_ID, OLD.CLUSTER_NAME);
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `METASTOR_PROCESSING_LOG`
--

DROP TABLE IF EXISTS `METASTOR_PROCESSING_LOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `METASTOR_PROCESSING_LOG` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `NOTIFICATION_ID` int(11) NOT NULL,
  `DATE_PROCESSED` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `END_TIME` datetime DEFAULT NULL,
  `SUCCESS` varchar(1) NOT NULL DEFAULT 'P',
  `CLUSTER_ID` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `ID_UNIQUE` (`ID`),
  KEY `FK_NOTIFICATION_ID_idx` (`NOTIFICATION_ID`),
  CONSTRAINT `FK_NOTIFICATION_ID` FOREIGN KEY (`NOTIFICATION_ID`) REFERENCES `DM_NOTIFICATION` (`ID`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=320200 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`MS_Hive_0_13`@`%`*/ /*!50003 TRIGGER `METASTOR_PROCESSING_LOG_AFTER_DELETE` AFTER DELETE ON `METASTOR_PROCESSING_LOG` FOR EACH ROW
BEGIN
  INSERT INTO METASTOR_PROCESSING_LOG_HIST (ID, NOTIFICATION_ID, DATE_PROCESSED, END_TIME, SUCCESS, CLUSTER_ID)
  VALUES (OLD.ID, OLD.NOTIFICATION_ID, OLD.DATE_PROCESSED, OLD.END_TIME, OLD.SUCCESS, OLD.CLUSTER_ID);
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `DB_AUTH`
--

DROP TABLE IF EXISTS `DB_AUTH`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DB_AUTH` (
  `DB_ID` bigint(20) NOT NULL,
  `USER` varchar(32) NOT NULL,
  PRIMARY KEY (`DB_ID`,`USER`),
  CONSTRAINT `FK_DB_AUTH` FOREIGN KEY (`DB_ID`) REFERENCES `DBS` (`DB_ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `EMR_CLUSTER`
--

DROP TABLE IF EXISTS `EMR_CLUSTER`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EMR_CLUSTER` (
  `CLUSTER_NAME` varchar(45) NOT NULL,
  `CLUSTER_ID` varchar(45) DEFAULT NULL,
  `CREATE_TIME` datetime DEFAULT NULL,
  `STATUS` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`CLUSTER_NAME`),
  UNIQUE KEY `CLUSTER_NAME_UNIQUE` (`CLUSTER_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `METASTOR_VERSION`
--

DROP TABLE IF EXISTS `METASTOR_VERSION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `METASTOR_VERSION` (
  `VERSION` varchar(16) NOT NULL,
  `DATE_UPDATED` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`VERSION`),
  UNIQUE KEY `VERSION_UNIQUE` (`VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `METASTOR_WORKFLOW`
--

DROP TABLE IF EXISTS `METASTOR_WORKFLOW`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `METASTOR_WORKFLOW` (
  `WORKFLOW_ID` int(11) NOT NULL,
  `PRIORITY` int(11) NOT NULL DEFAULT '10',
  `WORKFLOW_NAME` varchar(32) NOT NULL,
  PRIMARY KEY (`WORKFLOW_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

--
-- Table structure for table `METASTOR_OBJECT_LOCKS`
--

DROP TABLE IF EXISTS `METASTOR_OBJECT_LOCKS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `METASTOR_OBJECT_LOCKS` (
  `NAMESPACE` varchar(45) NOT NULL,
  `OBJ_NAME` varchar(100) NOT NULL,
  `USAGE_CODE` varchar(45) NOT NULL,
  `FILE_TYPE` varchar(45) NOT NULL,
  `CREATED_DATE` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `CLUSTER_ID` varchar(45) NOT NULL,
  `WORKER_ID` varchar(45) NOT NULL,
  `EXPIRATION_DT` datetime NOT NULL,
  PRIMARY KEY (`NAMESPACE`,`OBJ_NAME`,`USAGE_CODE`,`FILE_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-06-06 12:11:20

--
-- Table structure for table `DM_NOTIFICATION_HIST`
--

DROP TABLE IF EXISTS `DM_NOTIFICATION_HIST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DM_NOTIFICATION_HIST` (
  `ID` int(11) NOT NULL DEFAULT '0',
  `NAMESPACE` varchar(50) DEFAULT NULL,
  `OBJECT_DEF_NAME` varchar(100) NOT NULL,
  `USAGE_CODE` varchar(50) DEFAULT NULL,
  `FILE_TYPE` varchar(50) DEFAULT NULL,
  `DATE_CREATED` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `PARTITION_KEY` varchar(100) DEFAULT NULL,
  `PARTITION_VALUES` varchar(2500) DEFAULT NULL,
  `WF_TYPE` int(11) NOT NULL,
  `CORRELATION_DATA` text,
  `EXECUTION_ID` varchar(36) NOT NULL,
  `CLUSTER_NAME` varchar(45) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `METASTOR_PROCESSING_LOG_HIST`
--

DROP TABLE IF EXISTS `METASTOR_PROCESSING_LOG_HIST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `METASTOR_PROCESSING_LOG_HIST` (
  `ID` int(11) NOT NULL DEFAULT '0',
  `NOTIFICATION_ID` int(11) NOT NULL,
  `DATE_PROCESSED` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `END_TIME` datetime DEFAULT NULL,
  `SUCCESS` varchar(1) NOT NULL DEFAULT 'P',
  `CLUSTER_ID` varchar(45) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-11-26 10:51:27
