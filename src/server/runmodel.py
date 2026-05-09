import sys
import json
import torch
from predict.model import TransactionValueModel
from predict.my_dataloader import build_dataloader
def main():
    device = "cuda" if torch.cuda.is_available() else "cpu"
    device = torch.device(device)
    with open(sys.argv[1], "r") as f:
        data = json.load(f) 
    for k, v in data.items():
        data[k] = [v]
    pre_loader = build_dataloader(data)
    state_dict = torch.load("/SSD00/lyb/test/VPS/best_model.pth",map_location=device)
    my_model = TransactionValueModel().to(device)
    my_model.load_state_dict(state_dict)
    my_model.eval()
    with torch.no_grad():
            for type_em, params_em, context_em, value, items_id, items_quantity in pre_loader:
                type_em = type_em.to(device)
                params_em = params_em.to(device)
                context_em = context_em.to(device)
                value = value.to(device)
                items_id = items_id.to(device)
                items_quantity = items_quantity.to(device)


                pred = my_model(type_em, params_em, context_em,items_id,items_quantity)
                print(pred.item())

if __name__ == "__main__":
    main()