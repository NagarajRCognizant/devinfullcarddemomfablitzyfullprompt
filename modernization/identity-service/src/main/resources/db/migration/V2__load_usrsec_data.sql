-- The ten records of app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS, the file that app/jcl/DUSRSECJ.jcl
-- loads into the USRSEC cluster.
--
-- The source file is EBCDIC (cp037) with fixed 80 byte records; the values below are the decoded
-- fields, trailing spaces omitted because the CHAR columns re-apply them. Five administrators and
-- five regular users, all with the same password, exactly as the shipped file has them.

INSERT INTO security_user (sec_usr_id, sec_usr_fname, sec_usr_lname, sec_usr_pwd, sec_usr_type)
VALUES
    ('ADMIN001', 'MARGARET',  'GOLD',       'PASSWORD', 'A'),
    ('ADMIN002', 'RUSSELL',   'RUSSELL',    'PASSWORD', 'A'),
    ('ADMIN003', 'RAYMOND',   'WHITMORE',   'PASSWORD', 'A'),
    ('ADMIN004', 'EMMANUEL',  'CASGRAIN',   'PASSWORD', 'A'),
    ('ADMIN005', 'GRANVILLE', 'LACHAPELLE', 'PASSWORD', 'A'),
    ('USER0001', 'LAWRENCE',  'THOMAS',     'PASSWORD', 'U'),
    ('USER0002', 'AJITH',     'KUMAR',      'PASSWORD', 'U'),
    ('USER0003', 'LAURITZ',   'ALME',       'PASSWORD', 'U'),
    ('USER0004', 'AVERARDO',  'MAZZI',      'PASSWORD', 'U'),
    ('USER0005', 'LEE',       'TING',       'PASSWORD', 'U');
