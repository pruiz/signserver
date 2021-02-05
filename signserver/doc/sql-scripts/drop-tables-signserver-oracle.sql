-- Dropping tables for SignServer 3.5.x on Oracle
-- ------------------------------------------------------
-- Version: $Id: drop-tables-signserver-oracle.sql 9137 2018-02-22 14:09:13Z vinays $
-- Comment: 


--
-- Drop table `AuditRecordData`
--
DROP TABLE "AUDITRECORDDATA";


--
-- Drop table `GlobalConfigurationData`
--
DROP TABLE "GLOBALCONFIGDATA";


--
-- Drop table `signerconfigdata`
--
DROP TABLE "SIGNERCONFIGDATA";


--
-- Drop table `KeyUsageCounter`
--
DROP TABLE "KEYUSAGECOUNTER";


--
-- Drop table `ArchiveData`
--
DROP TABLE "ARCHIVEDATA";


--
-- Drop table `KeyData`
--
DROP TABLE "KeyData";


--
-- Drop sequence `HIBERNATE_SEQUENCE`
--
DROP SEQUENCE "HIBERNATE_SEQUENCE";


-- End
