declare v_date as date;
declare c_date as date = date '2019-02-20 12:23:45.678';
select v into v_date from table (values c_date) as tb(v) where v = c_date;
print('date = ' || v_date);

declare v_datetime as timestamp(17, 3);
declare c_datetime as timestamp(17, 3) = timestamp '2019-02-20 12:23:45.678';
select v into v_datetime from table (values c_datetime) as tb(v) where v = c_datetime;
print('datetime = ' || v_datetime);
