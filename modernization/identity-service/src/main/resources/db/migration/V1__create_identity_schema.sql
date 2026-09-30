-- Card Demo identity schema: the converted USRSEC file.
--
-- Replaces the VSAM KSDS cluster defined by app/jcl/DUSRSECJ.jcl (KEYS(8,0), RECORDSIZE(80,80)) and
-- listed in app/catlg/LISTCAT.txt. One table for the single record layout of app/cpy/CSUSR01Y.cpy,
-- so a row maps one to one onto an 80 byte record.
--
-- Every field is CHAR of the length of its PIC clause: the programs move fixed length fields in and
-- out of the record and compare them padded with spaces (COUSR02C compares the screen field with
-- SEC-USR-FNAME directly), so the padding is part of the behaviour and is kept in the column type.
-- SEC-USR-PWD holds the password exactly as the source file does, in clear text; that is a
-- deliberate carry-over of the legacy data and is recorded as a risk in the unsupported construct
-- register rather than silently changed.

CREATE TABLE security_user (
    sec_usr_id     CHAR(8)  NOT NULL,
    sec_usr_fname  CHAR(20) NOT NULL,
    sec_usr_lname  CHAR(20) NOT NULL,
    sec_usr_pwd    CHAR(8)  NOT NULL,
    sec_usr_type   CHAR(1)  NOT NULL,
    sec_usr_filler CHAR(23) NOT NULL DEFAULT '',
    CONSTRAINT pk_security_user PRIMARY KEY (sec_usr_id)
);

COMMENT ON TABLE security_user IS 'USRSEC KSDS - app/cpy/CSUSR01Y.cpy SEC-USER-DATA';
COMMENT ON COLUMN security_user.sec_usr_type IS
    'A = administrator (CDEMO-USRTYP-ADMIN), any other value is a regular user';
COMMENT ON COLUMN security_user.sec_usr_filler IS
    'SEC-USR-FILLER: part of the record layout, never read or written by any program';
