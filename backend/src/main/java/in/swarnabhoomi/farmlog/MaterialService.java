package in.swarnabhoomi.farmlog;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialService {
  private final TradeService sql;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public MaterialService(TradeService sql, org.springframework.jdbc.core.JdbcTemplate db) {
    this.sql = sql;
    this.db = db;
  }

  public record MaterialInput(
      String name,
      String category,
      String unit,
      BigDecimal openingStock,
      BigDecimal lowThreshold,
      BigDecimal unitCost) {}

  public record Usage(Long materialId, BigDecimal quantity) {}

  public record Adjustment(BigDecimal quantity, String reason, String requestKey) {}

  void require(AppUser u) {
    TradeService.role(u, "farmer", "seller");
  }

  static void nonnegative(BigDecimal q) {
    if (q == null
        || q.signum() < 0
        || q.scale() > 3
        || q.compareTo(new BigDecimal("1000000000")) > 0)
      throw TradeService.bad("प्रमाण शून्य किंवा त्यापेक्षा जास्त असावे.");
  }

  @Transactional
  public long create(MaterialInput input, AppUser u) {
    require(u);
    nonnegative(input.openingStock());
    nonnegative(input.lowThreshold());
    if (input.unitCost() != null
        && (input.unitCost().signum() < 0
            || input.unitCost().scale() > 2
            || input.unitCost().compareTo(new BigDecimal("9999999999.99")) > 0))
      throw TradeService.bad("साहित्याची किंमत तपासा.");
    long id =
        sql.insert(
            "INSERT INTO"
                + " farm_material(owner_id,name,category,unit,quantity,low_threshold,unit_cost,created_at)"
                + " VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            u.getId(),
            TradeService.text(input.name(), 120),
            TradeService.text(input.category(), 40),
            TradeService.text(input.unit(), 16),
            input.openingStock(),
            input.lowThreshold(),
            input.unitCost());
    db.update(
        "INSERT INTO farm_material_movement(material_id,owner_id,quantity,reason,created_at)"
            + " VALUES(?,?,?,'OPENING',CURRENT_TIMESTAMP)",
        id,
        u.getId(),
        input.openingStock());
    return id;
  }

  public List<Map<String, Object>> list(AppUser u) {
    require(u);
    return sql.rows("SELECT * FROM farm_material WHERE owner_id=? ORDER BY name,id", u.getId());
  }

  public List<Map<String, Object>> history(Long id, AppUser u) {
    require(u);
    sql.one("SELECT id FROM farm_material WHERE id=? AND owner_id=?", id, u.getId());
    return sql.rows(
        "SELECT * FROM farm_material_movement WHERE material_id=? AND owner_id=? ORDER BY id DESC"
            + " LIMIT 200",
        id,
        u.getId());
  }

  @Transactional
  public void adjust(Long id, Adjustment input, AppUser u) {
    require(u);
    if (input.quantity() == null
        || input.quantity().signum() == 0
        || input.quantity().abs().compareTo(new BigDecimal("1000000000")) > 0
        || input.quantity().scale() > 3
        || input.requestKey() == null
        || !input.requestKey().matches("[a-f0-9-]{36}")) throw TradeService.bad("प्रमाण तपासा.");
    String reason = TradeService.text(input.reason(), 120);
    var m =
        sql.one("SELECT * FROM farm_material WHERE id=? AND owner_id=? FOR UPDATE", id, u.getId());
    var previous =
        sql.rows("SELECT * FROM farm_material_movement WHERE request_key=?", input.requestKey());
    if (!previous.isEmpty()) {
      if (TradeService.id(previous.get(0), "ownerId") != u.getId()
          || TradeService.id(previous.get(0), "materialId") != id
          || TradeService.decimal(previous.get(0), "quantity").compareTo(input.quantity()) != 0)
        throw TradeService.conflict("विनंती क्रमांक आधी वापरला आहे.");
      return;
    }
    BigDecimal remaining = TradeService.decimal(m, "quantity").add(input.quantity());
    nonnegative(remaining);
    db.update("UPDATE farm_material SET quantity=? WHERE id=?", remaining, id);
    db.update(
        "INSERT INTO"
            + " farm_material_movement(material_id,owner_id,quantity,reason,request_key,created_at)"
            + " VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
        id,
        u.getId(),
        input.quantity(),
        reason,
        input.requestKey());
  }

  public List<Usage> usages(Long entryId, AppUser u) {
    return db.query(
        "SELECT material_id,-SUM(quantity) AS used FROM farm_material_movement WHERE entry_id=? AND"
            + " owner_id=? GROUP BY material_id HAVING SUM(quantity)<0",
        (r, n) -> new Usage(r.getLong(1), r.getBigDecimal(2)),
        entryId,
        u.getId());
  }

  void hydrate(FarmEntry entry, AppUser u) {
    hydrateWith(entry, usageRows(u, entry.getId()));
  }

  void hydrateAll(List<FarmEntry> entries, AppUser u) {
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    for (var row : usageRows(u, null))
      grouped.computeIfAbsent(TradeService.id(row, "entryId"), id -> new ArrayList<>()).add(row);
    entries.forEach(entry -> hydrateWith(entry, grouped.getOrDefault(entry.getId(), List.of())));
  }

  private List<Map<String, Object>> usageRows(AppUser user, Long entryId) {
    String query =
        "SELECT v.entry_id,v.material_id,-SUM(v.quantity) AS quantity,MAX(m.name) AS"
            + " name,MAX(m.unit) AS unit FROM farm_material_movement v JOIN farm_material m ON"
            + " m.id=v.material_id WHERE v.owner_id=? AND v.entry_id IS NOT NULL";
    String group = " GROUP BY v.entry_id,v.material_id HAVING SUM(v.quantity)<0";
    return entryId == null
        ? sql.rows(query + group, user.getId())
        : sql.rows(query + " AND v.entry_id=?" + group, user.getId(), entryId);
  }

  private void hydrateWith(FarmEntry entry, List<Map<String, Object>> rows) {
    entry.setMaterialUsages(
        rows.stream()
            .map(
                row ->
                    new Usage(
                        TradeService.id(row, "materialId"), TradeService.decimal(row, "quantity")))
            .toList());
    entry.setMaterialUsageSummary(
        rows.isEmpty()
            ? null
            : rows.stream()
                .map(
                    row ->
                        row.get("name")
                            + ": "
                            + TradeService.decimal(row, "quantity")
                                .stripTrailingZeros()
                                .toPlainString()
                            + " "
                            + row.get("unit"))
                .collect(java.util.stream.Collectors.joining(", ")));
  }

  /**
   * Called inside the diary transaction. Editing restores the old consumption before applying its
   * replacement.
   */
  void sync(Long entryId, List<Usage> desired, AppUser u) {
    if (desired == null) return;
    require(u);
    if (desired.size() > 40) throw TradeService.bad("एका नोंदीत जास्तीत जास्त ४० साहित्य निवडा.");
    var wanted = new TreeMap<Long, BigDecimal>();
    for (var use : desired) {
      if (use.materialId() == null) throw TradeService.bad("साहित्य निवडा.");
      TradeService.quantity(use.quantity());
      wanted.merge(use.materialId(), use.quantity(), BigDecimal::add);
    }
    var old = new TreeMap<Long, BigDecimal>();
    for (var use : usages(entryId, u)) old.put(use.materialId(), use.quantity());
    var ids = new TreeSet<Long>();
    ids.addAll(wanted.keySet());
    ids.addAll(old.keySet());
    for (Long id : ids) {
      var m =
          sql.one(
              "SELECT * FROM farm_material WHERE id=? AND owner_id=? FOR UPDATE", id, u.getId());
      BigDecimal delta =
          old.getOrDefault(id, BigDecimal.ZERO).subtract(wanted.getOrDefault(id, BigDecimal.ZERO));
      if (delta.signum() == 0) continue;
      BigDecimal remaining = TradeService.decimal(m, "quantity").add(delta);
      if (remaining.signum() < 0)
        throw TradeService.conflict(m.get("name") + ": पुरेसा साठा नाही.");
      nonnegative(remaining);
      db.update("UPDATE farm_material SET quantity=? WHERE id=?", remaining, id);
      db.update(
          "INSERT INTO"
              + " farm_material_movement(material_id,owner_id,entry_id,quantity,reason,created_at)"
              + " VALUES(?,?,?,?,'DIARY',CURRENT_TIMESTAMP)",
          id,
          u.getId(),
          entryId,
          delta);
    }
  }
}
