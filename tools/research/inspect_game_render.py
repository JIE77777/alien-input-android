from pathlib import Path
import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes
import json,argparse

parser=argparse.ArgumentParser(description='Read targeted rendering metadata and IL; never modifies the game.')
parser.add_argument('--game-data',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args();root=args.game_data
pe=dnfile.dnPE(str(root/'Managed/Assembly-CSharp.dll'))
out=args.output;out.mkdir(parents=True,exist_ok=True)
tables={0x01:'TypeRef',0x02:'TypeDef',0x04:'Field',0x06:'MethodDef',0x0A:'MemberRef',0x11:'StandAloneSig',0x1B:'TypeSpec',0x2B:'MethodSpec'}
def resolve(operand):
    if not hasattr(operand,'value'):return str(operand)
    value=operand.value;table=value>>24;rid=value&0xffffff
    try:
        if table==0x70:return repr(pe.net.user_strings.get(rid).value)
        row=getattr(pe.net.mdtables,tables[table]).rows[rid-1]
        return str(getattr(row,'Name',getattr(row,'TypeName',operand)))
    except Exception:return str(operand)

types=[];lines=[]
names={'DrawLine','DrawLineRenderer','DrawRect','DrawCircle','DrawPolygon','DrawStar','DrawDot','DrawArrow','DrawACSprite','DrawACToSlot','DrawPath','DrawPath1','GetCharacter','StringToCharacter','FrontColor','BackColor','DefaultColor'}
for typ in pe.net.mdtables.TypeDef.rows:
    name=str(typ.TypeName)
    methods=[str(x.row.Name) for x in typ.MethodList]
    if name=='AlienCharacter' or any(x in names for x in methods):
        types.append({'type':name,'fields':[str(x.row.Name) for x in typ.FieldList],'methods':methods})
        for index in typ.MethodList:
            m=index.row;method=str(m.Name)
            if method not in names and not(name in ['AlienCharacter','Draw'] and method=='.ctor') and not(name=='Global' and method=='DefaultSetting'):continue
            if not m.Rva:continue
            lines.append('\n### '+name+'.'+method)
            try:
                body=read_method_body_from_bytes(pe.get_data(m.Rva,65536))
                for ins in body.instructions:lines.append(f'{ins.offset:04x} {ins.opcode.name} {resolve(ins.operand) if ins.operand is not None else ""}')
            except Exception as e:lines.append('ERROR '+str(e))
(out/'render-types.json').write_text(json.dumps(types,ensure_ascii=False,indent=2))
(out/'render-il.txt').write_text('\n'.join(lines))
for t in types:print(t['type'],len(t['fields']),'fields',len(t['methods']),'methods')
print('Saved',len(lines),'IL lines for targeted rendering methods')
