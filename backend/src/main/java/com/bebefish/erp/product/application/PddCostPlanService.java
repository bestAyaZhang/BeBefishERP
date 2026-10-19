package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.api.PageResponse;
import com.bebefish.erp.product.application.PddCostCalculator.Line;
import com.bebefish.erp.product.application.PddCostCalculator.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PddCostPlanService {
    public record SavePlan(String name, String remark, Long version, List<Line> lines) {}
    public record CalculatedLine(Line input, Result result) {}
    public record Summary(long id, String name, String remark, long version, boolean archived,
            int lineCount, LocalDateTime updatedAt) {}
    public record Plan(long id, String name, String remark, long version, boolean archived,
            int formulaVersion, List<CalculatedLine> lines, LocalDateTime updatedAt) {}
    public record SkuOption(long skuId, String skuCode, String productName, String specification,
            BigDecimal unitCost, BigDecimal salePrice) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PddCostCalculator calculator;
    public PddCostPlanService(JdbcTemplate jdbc, ObjectMapper mapper, PddCostCalculator calculator) {
        this.jdbc = jdbc; this.mapper = mapper; this.calculator = calculator;
    }

    public PageResponse<Summary> list(String keyword, boolean archived, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || page > 1000000) throw PddCostCalculator.invalid("分页参数无效");
        var query = keyword == null ? "" : keyword.trim();
        if (query.length() > 100) throw PddCostCalculator.invalid("搜索内容最多100字");
        var pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var where = " where archived=? and plan_name like ? escape '!'";
        var total = jdbc.queryForObject("select count(*) from pdd_sku_cost_plan" + where, Long.class, archived, pattern);
        var records = jdbc.query("select * from pdd_sku_cost_plan" + where + " order by updated_at desc,id desc limit ? offset ?",
                (rs, n) -> new Summary(rs.getLong("id"),rs.getString("plan_name"),rs.getString("remark"),
                        rs.getLong("version_no"),rs.getBoolean("archived"),decode(rs.getString("lines_json")).size(),
                        rs.getObject("updated_at", LocalDateTime.class)), archived, pattern, size, (long)(page-1)*size);
        return new PageResponse<>(records, page, size, total);
    }

    public Plan get(long id) {
        return jdbc.query("select * from pdd_sku_cost_plan where id=?", (rs,n) -> new Plan(
                rs.getLong("id"),rs.getString("plan_name"),rs.getString("remark"),rs.getLong("version_no"),
                rs.getBoolean("archived"),rs.getInt("formula_version"),decode(rs.getString("lines_json")).stream()
                        .map(line -> new CalculatedLine(line,calculator.calculate(line))).toList(),
                rs.getObject("updated_at", LocalDateTime.class)), id).stream().findFirst().orElseThrow(() ->
                new BusinessException("COST_PLAN_NOT_FOUND", HttpStatus.NOT_FOUND,"成本方案不存在"));
    }

    public List<SkuOption> options(String keyword) {
        var query = keyword == null ? "" : keyword.trim();
        if(query.length()>100) throw PddCostCalculator.invalid("搜索内容最多100字");
        var pattern = "%" + query.replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        return jdbc.query("""
                select s.id,s.sku_code,p.product_name,coalesce(nullif(s.spec_text,''),s.sku_name,'默认规格') as specification,
                       s.standard_cost,s.default_sale_price
                from product_sku s join product_spu p on p.id=s.product_id
                where s.status='enabled' and p.status='enabled'
                  and (p.product_name like ? escape '!' or p.item_no like ? escape '!'
                    or s.sku_code like ? escape '!' or s.sku_name like ? escape '!')
                order by p.id,s.id limit 50
                """, (rs,n)->new SkuOption(rs.getLong("id"),rs.getString("sku_code"),rs.getString("product_name"),
                rs.getString("specification"),rs.getBigDecimal("standard_cost"),rs.getBigDecimal("default_sale_price")),
                pattern,pattern,pattern,pattern);
    }

    @Transactional
    public Plan save(Long id, SavePlan request, String operator) {
        if (request == null || request.name()==null || request.name().isBlank() || request.name().trim().length()>120)
            throw PddCostCalculator.invalid("请填写方案名称，最多120字");
        if (request.remark()!=null && request.remark().length()>1000) throw PddCostCalculator.invalid("备注最多1000字");
        if(request.lines()==null || request.lines().isEmpty() || request.lines().size()>200)
            throw PddCostCalculator.invalid("每个方案需要1至200条规格");
        var normalized = request.lines().stream().map(line -> {
            calculator.calculate(line);
            if (line.specification()==null || line.specification().isBlank() || line.specification().length()>200
                    || (line.skuCode()!=null && line.skuCode().length()>100)) throw PddCostCalculator.invalid("请填写规格名称，最多200字，SKU编码最多100字");
            var skuCode = line.skuCode()==null ? "" : line.skuCode().trim();
            if(line.skuId()!=null) {
                var codes=jdbc.queryForList("select s.sku_code from product_sku s join product_spu p on p.id=s.product_id "
                        + "where s.id=? and s.status='enabled' and p.status='enabled'", String.class,line.skuId());
                if(codes.isEmpty()) throw PddCostCalculator.invalid("关联SKU不存在或已停用，请移除关联后保存");
                skuCode=codes.getFirst();
            }
            return new Line(line.skuId(),skuCode,line.specification().trim(),line.unitCost(),line.shippingCost(),
                    line.packagingCost(),line.otherCost(),line.salePrice(),line.refundRate(),line.platformFeeRate(),
                    line.adMode(),"roi".equals(line.adMode())?line.roi():null,"cpa".equals(line.adMode())?line.adCost():null);
        }).toList();
        var json = encode(normalized);
        if(id==null) {
            var keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement=connection.prepareStatement("insert into pdd_sku_cost_plan(plan_name,remark,lines_json,created_by,updated_by,created_at,updated_at) "
                        + "values(?,?,?,?,?,now(3),now(3))", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1,request.name().trim()); statement.setString(2,request.remark()==null?"":request.remark().trim());
                statement.setString(3,json); statement.setString(4,operator); statement.setString(5,operator);
                return statement;
            },keys);
            id=java.util.Objects.requireNonNull(keys.getKey()).longValue();
        } else {
            if(request.version()==null) throw PddCostCalculator.invalid("缺少方案版本，请重新打开方案");
            int changed=jdbc.update("update pdd_sku_cost_plan set plan_name=?,remark=?,lines_json=?,version_no=version_no+1,updated_by=?,updated_at=now(3) "
                    + "where id=? and version_no=? and archived=false",request.name().trim(),request.remark()==null?"":request.remark().trim(),json,operator,id,request.version());
            if(changed!=1) throw conflict();
        }
        return get(id);
    }

    @Transactional
    public void archive(long id, long version, boolean archived, String operator) {
        int changed=jdbc.update("update pdd_sku_cost_plan set archived=?,version_no=version_no+1,updated_by=?,updated_at=now(3) "
                + "where id=? and version_no=? and archived=?",archived,operator,id,version,!archived);
        if(changed!=1) throw conflict();
    }
    private BusinessException conflict() { return new BusinessException("COST_PLAN_CONFLICT",HttpStatus.CONFLICT,"方案已被修改或归档，请重新打开后操作"); }
    private String encode(List<Line> lines) {
        try { return mapper.writeValueAsString(lines); } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    private List<Line> decode(String json) {
        try { return mapper.readValue(json,new TypeReference<List<Line>>(){}); } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("成本方案数据无法读取",error); }
    }
}
