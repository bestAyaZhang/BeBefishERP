-- V23 stored every displayed label. Retain only labels that differ from the generated rule
-- as explicit exceptions; the other rows become dynamic when a shop or platform is renamed.
update platform_shop s join sales_platform p on p.id = s.platform_id
set s.option_label = null
where s.option_label = case
    when s.channel_type = 'private' then concat('私域_',
        case when p.platform_name like '%代发' then '代发' else p.platform_name end,
        '_', s.shop_name)
    else concat('电商_', p.platform_name, '_', s.shop_name)
end;
