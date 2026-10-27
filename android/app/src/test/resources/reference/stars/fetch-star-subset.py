"""Fetch exactly the pinned HYG source rows; no full catalogue is retained."""
from pathlib import Path
import csv,hashlib,io,json,urllib.request
root=Path(__file__).resolve().parent
hips={54061,53910,58001,59774,62956,65378,67301,27989,24436,25336,25930,26311,26727,27366,26207,3179,746,4427,6686,8886,60718,62434,61084,59747,60260}
url='https://raw.githubusercontent.com/astronexus/HYG-Database/c7f7f883fe678cc7680169a50ccd7dcc49b060ce/hyg/CURRENT/hygdata_v41.csv'
rows=[]
with urllib.request.urlopen(url) as f:
 reader=csv.DictReader(io.TextIOWrapper(f));fields=reader.fieldnames
 for row in reader:
  if row['hip'] and int(row['hip']) in hips:
   rows.append(row)
   if len(rows)==len(hips):break
assert {int(r['hip']) for r in rows}==hips
buf=io.StringIO(newline='');writer=csv.DictWriter(buf,fieldnames=fields);writer.writeheader();writer.writerows(rows)
data=buf.getvalue().encode('utf-8');sha=hashlib.sha256(data).hexdigest()
assert sha=='6bc120de3163dca15d2cf49cf78bae688261cfa31ff05035800705dabd0a76ec',sha
(root/'hyg-v41-25stars-raw.csv').write_bytes(data)
print(json.dumps(dict(sourceUrl=url,read='partial stream stopped after all selected HIP rows; no full source SHA256 claim',rows=len(rows),subsetSha256=sha)))
