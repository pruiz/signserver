-- Dropping tables for SignServer 3.5.x on MySQL/MariaDB
-- ------------------------------------------------------
-- Version: $Id: drop-tables-signserver-mysql.sql 8929 2017-12-20 15:42:29Z vinays $
-- Comment: 


--
-- Drop table `AuditRecordData`
--
DROP TABLE IF EXISTS `AuditRecordData`;

--
-- Drop table `GlobalConfigData`
--
DROP TABLE IF EXISTS `GlobalConfigData`;


--
-- Drop table `signerconfigdata`
--
DROP TABLE IF EXISTS `signerconfigdata`;


--
-- Drop table `KeyUsageCounter`
--
DROP TABLE IF EXISTS `KeyUsageCounter`;


--
-- Drop table `ArchiveData`
--
DROP TABLE IF EXISTS `ArchiveData`;


--
-- Drop table `KeyData`
--
DROP TABLE IF EXISTS `KeyData`;


--
-- Drop table `SEQUENCE`
--
DROP TABLE IF EXISTS `SEQUENCE`;


-- End
