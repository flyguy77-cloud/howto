use master
GO
create database sportschool
GO
use sportschool
GO

create table abonnement (
    code            char(2)     not null,
    naam            varchar(25) not null,
    aantal_maanden  smallint    not null,
    totaalprijs     decimal(10) not null,
    inclusief_sauna char(1)     not null,
    constraint abonnement_pk primary key(code)
);

create table lid (
    nr          char(4)     not null,
    naam        varchar(50) not null,
    woonplaats  varchar(50) not null,
    telefoon    varchar(10) null,
    geslacht    char(1)     not null,
    abonnement  char(2)     not null,
    constraint lid_pk primary key (nr),
    constraint lid_abonnement_fk foreign key(abonnement) references abonnement(code)
    
);

create table fitnessapparaat (
    nr                  char(3)     not null,
    naam                varchar(50) not null,
    type                varchar(25) not null,
    spiergroep          varchar(25) not null,
    maximaal_gewicht    integer     null,
    constraint fitnessapparaat_pk primary key(nr)
);

create table workout (
    lid             char(4)     not null,
    fitnessapparaat char(3)     not null,
    datum           date        not null,
    aantal_moves    integer     null,
    gebruik_gewicht integer     null,
    tijdsduur       integer     null,
    verbrande_calorieen integer not null,
    constraint workout_pk primary key(lid,fitnessapparaat),
    constraint workout_lid_fk foreign key (lid) references lid(nr),
    constraint workout_fitnessapparaat_fk foreign key(fitnessapparaat) references fitnessapparaat(nr)
);

insert into abonnement values 
('B', 'Basic',1,30,'N'),
('BP','Basic Plus',1,35,'J'),
('L','Large',12,330,'N'),
('LP','Large Plus',12,385,'J'),
('XL','X-Large',24,660,'J');

insert into lid values
('L001','Jan Heijer','Den Haag','0634825194','M','XL'),
('L002','Jessica Boom','Den Haag','0703406238','V','B'),
('L003','Kees Kesters','Voorburg','0645680624','M','LP'),
('L004','Inge Leeflang','Rijswijk',NULL,'V','XL');

insert into fitnessapparaat values
('F01','Rotary Torso','Kracht','Buik',100),
('F02','Shoulder Press','Kracht','Schouders',70),
('F03','Treadmill','Cardio','Benen',NULL),
('F04','Bicycle','Cardio','Benen',NULL),
('F05','Leg Press','Kracht','Benen',200),
('F06','Rowing Boat','Cardio','Schouders',NULL);

insert into workout values
('L001','F06','12/04/2017',NULL,NULL,30,200),
('L002','F03','12/04/2017',NULL,NULL,12,105),
('L001','F02','12/04/2017',30,60,NULL,18),
('L004','F04','12/04/2017',NULL,NULL,60,340),
('L001','F03','12/06/2017',NULL,NULL,20,150),
('L004','F03','12/06/2017',NULL,NULL,40,290),
('L001','F05','12/06/2017',30,120,NULL,36),
('L004','F05','12/08/2017',24,70,NULL,17);