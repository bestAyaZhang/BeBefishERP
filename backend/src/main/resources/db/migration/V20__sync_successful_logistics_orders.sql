update shipment s
join shipment_logistics_order o on o.shipment_id = s.id
set s.logistics_company = '安能物流',
    s.tracking_no = o.tracking_no,
    s.status = 'completed',
    s.version_no = s.version_no + 1,
    s.updated_by = o.operator_id,
    s.updated_at = now(3)
where o.state = 'succeeded'
  and o.tracking_no <> ''
  and (s.logistics_company <> '安能物流'
       or s.tracking_no <> o.tracking_no
       or s.status <> 'completed');
