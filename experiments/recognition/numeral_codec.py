"""Exact integer notation from production Numerals.java; phrase grouping stays unknown."""
import re
ROWS=[[],['F1302','F1304','F1305','F1306'],['F1310','F1311','F1313','F1314'],
      ['F1317','F1318','F1320','F1321'],['F1324','F1325','F1327','F1328'],
      ['F1331','F1332','F1334','F1335'],['F1338','F1339','F1341','F1342']]
DYNAMIC_IDS=[f'N{c}P{p}' for c in range(1,7) for p in range(4,22)]
COMPONENTS={gid:(c,p) for c,row in enumerate(ROWS) for p,gid in enumerate(row)}
COMPONENTS.update({f'N{c}P{p}':(c,p) for c in range(1,7) for p in range(4,22)})
COMPONENTS['F1301']=(0,0)

def encode_integer(value):
    text=str(value)
    if not re.fullmatch(r'[+-]?[0-9]{1,18}',text):
        raise ValueError('Integer must have at most 18 decimal digits')
    value=int(text)
    if value==0:return ['F1301']
    result=[];n=abs(value);power=0
    while n:
        c=n%7
        if c:result.append(ROWS[c][power] if power<4 else f'N{c}P{power}')
        n//=7;power+=1
    return (['F0098'] if value<0 else [])+result[::-1]

def integer_candidate(ids):
    """Interpret only a complete canonical run. Never infer adjacent-number boundaries."""
    if not ids:return None
    negative=ids[0]=='F0098';body=ids[1:] if negative else ids
    if not body or any(g not in COMPONENTS for g in body):return None
    value=sum(COMPONENTS[g][0]*7**COMPONENTS[g][1] for g in body)
    if negative:value=-value
    try:
        if encode_integer(value)!=ids:return None
    except ValueError:return None
    return str(value)

def numeric_candidates(ids):
    result=[];i=0
    while i<len(ids):
        if ids[i] not in COMPONENTS and ids[i]!='F0098':i+=1;continue
        start=i;i+=1
        while i<len(ids) and ids[i] in COMPONENTS:i+=1
        run=ids[start:i];value=integer_candidate(run)
        if value is not None:
            result.append(dict(start=start,end=i,glyphs=run,decimal=value,
                grouping='assumed_contiguous_number',requires_group_confirmation=True))
    return result
