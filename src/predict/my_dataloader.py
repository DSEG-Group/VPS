import torch
from torch.utils.data import Dataset, DataLoader

class TPCCValueDataset(Dataset):
    def __init__(self, features: dict):
        self.types = torch.tensor(features["type_onehots"], dtype=torch.long)
        self.params = torch.tensor(features["inputdatas"], dtype=torch.float32)
        self.contexts = torch.tensor(features["contexts"], dtype=torch.float32)
        self.values = torch.tensor(features["values"], dtype=torch.float32)
        self.items_id = torch.tensor(features["items_id"],dtype=torch.float32)
        self.items_quantity = torch.tensor(features["items_quantity"],dtype=torch.float32)
        self.item_len = self.items_id.__len__()
        self.mask = (self.items_id != 0).float()


        # self.p_mean = torch.mean(self.params)
        # self.p_std = torch.std(self.params)
        # self.c_mean = torch.mean(self.contexts)
        # self.c_std = torch.std(self.contexts)
        # self.v_mean = torch.mean(self.values)
        # self.v_std = torch.std(self.values)

        # self.p_norm = (self.params - self.p_mean) / self.p_std
        # self.c_norm = (self.contexts - self.c_mean) / self.c_std
        # self.v_norm = (self.values - self.v_mean)/self.v_std

    def __len__(self):
        return len(self.types)

    def __getitem__(self, idx):
        return (
            self.types[idx],
            self.params[idx],
            self.contexts[idx],
            self.values[idx],
            self.items_id[idx],
            self.items_quantity[idx]
        )
    
    def get_vmean_vstd():
        return self.v_mean,self.v_std

def build_dataloader(features, batch_size=64, shuffle=True, num_workers=0):
    dataset = TPCCValueDataset(features)
    return DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=shuffle,
        num_workers=num_workers,
        drop_last=False
    )