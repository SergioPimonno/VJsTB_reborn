"""Собирает SQL для обновления серверных синглтонов GUIDE_TEXT и ONBOARDING_TEXT из JSON, выгруженных DumpSections.

Использование: python make_sql.py <каталог с GUIDE_NEW.json и ONBOARDING_NEW.json>
Пишет <каталог>/update_texts.sql. Правило обновления то же, что делает LibraryService.update: revision+1, новый
global_seq из последовательности (иначе клиенты не увидят изменение в дельте /changes), updated_at, deleted=false.
"""
import json
import os
import sys

directory = sys.argv[1]
items = [("global-guide-text", "GUIDE_NEW.json"), ("global-onboarding-text", "ONBOARDING_NEW.json")]
out = ["BEGIN;"]
for item_id, fn in items:
    with open(os.path.join(directory, fn), encoding="utf-8") as f:
        payload = json.dumps(json.load(f), ensure_ascii=False, separators=(",", ":"))
    assert "$pl$" not in payload
    out.append(
        "UPDATE library_items SET payload_json = $pl$%s$pl$, revision = revision + 1, "
        "global_seq = nextval('library_global_seq'), updated_at = now(), deleted = false "
        "WHERE id = '%s' RETURNING id, global_seq, length(payload_json);" % (payload, item_id))
out.append("COMMIT;")
target = os.path.join(directory, "update_texts.sql")
with open(target, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(out) + "\n")
print("written", target)
