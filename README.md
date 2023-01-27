# NeoCompounder

## Introduction

`NEO` (Neo) is a native asset on the Neo blockchain that allows holders to claim GAS rewards in exchange for participating in Neo Governance through voting.


`bNEO` (BurgerNeo) is a token that encapsulates the voting process and allows holders to maximize their voting rewards without worrying about periodically casting a vote for a different candidate.
`1.00000000 bNEO` can be exchanged for `1 NEO` at any time.


NeoCompounder introduces a new token `cNEO` (CompoundingNeo) that takes the `GAS` rewards from voting and compounds it into more underlying `bNEO`, with the end result being that each `cNEO` token becomes worth more and more `bNEO` over time.

Users can mint new `cNEO` at any time by depositing `NEO` or `bNEO` in the `cNEO` contract and minting the equivalent value of `cNEO` tokens.
They can later burn their `cNEO` tokens to redeem the underlying `bNEO`, which will be a greater quantity due to the `GAS` compounding.

---

## Contract Deployments

### Testnet

Contract Address: `NaWuywUm5W91L1VMk41Cy2iSvixmDoMaKT`

Script Hash: `0x74062287a66ddf4986ba2c5d7c20e09b32a52da0`

### Mainnet

Contract Address: `Not yet deployed`

Script Hash: `Not yet deployed`

---

## How Does cNEO Work?

### Initial State
`cNEO` starts out with a `totalSupply == 0`.
At this point, we also have that `bneoReserves == 0`

### First Mint
When a user decides to mint `cNEO`, they can transfer either `NEO` or `bNEO` to the `cNEO` contract address.
If the user transfers `x bNEO`, the contract mints `x cNEO` for the user and updates `bneoReserves` to `x`.
If the user transfers `x NEO`, the contract mints `x bNEO` by locking up this `NEO`, mints `x cNEO` for the user and updates `bneoReserves` to `x`.

### Compound
Compounding follows the following steps:
1. A caller invokes the `compound` method. This is only callable once every `compoundPeriod`.
2. The `cNEO` contract claims `GAS` for all of its underlying `bNEO` reserves.
3. The `cNEO` contract sets aside `feePercent GAS` for its operations.
5. The `cNEO` contract takes the remaining `GAS` and swaps it for `y bNEO` on the Flamingo `bNEO-GAS` pool.
6. The `cNEO` contract updates `bneoReserves = x + y` 
7. The `cNEO` contract rewards the caller of `compound` with a small amount of GAS.

An invocation of `compound` is expected to cost `~0.23 GAS`.
The caller will be rewarded with a small bonus over this quantity to cover the invocation fees and pourboire.

### Burn
After the first mint and comounding, we now have `x cNEO` backed by `x + y bNEO`.
When a user burns `cNEO`, they will now receive `(x + y) / x bNEO` for every `cNEO` burned.
For example, if `x == 10` and `y == 1`, then each `cNEO` can be burned for `1.1 bNEO`.

### Additional Mint
We still have the ratio of `x cNEO` to `x + y bNEO`.
Any new `cNEO` mints will now be minted in the ratio of `x / (x + y) cNEO` per `x bNEO`.
For example, if `x == 10` and `y == 1`, then each `bNEO` will now mint `1 / 1.1 cNEO`.

### Max Supply
Initially, `cNEO` will have a `maxSupply` of `1_000_000.00000000`.
This is another mechanism to prevent attackers from profiting from the `compound` call, as this limits the size of the `GAS` swap.
This cap is adjustable and will be revisited if it is ever close to being breached.

### Top Up GAS
With enough `bNEO` reserves, NeoCompounder expects to be able to fund its own operations through the `feePercent GAS` that it sets aside from each compound operation.
Until then, anyone can top up the contract's `GAS` reserves by tranferring `GAS` to the contract with a single string parameter `TOP_UP_GAS`.

---

## User Actions

### Mint

#### Mint From bNEO

A user can mint `cNEO` simply by transferring `bNEO` to the `cNEO` contract with no parameters.

```
bNEO.transfer(account, cNEO, quantity, null), where

bNEO      is the NeoBurger contract
account   is the address that wishes to mint cNEO
cNEO      is the NeoCompounder contract
quantity  is the quantity of bNEO to be converted into cNEO 
```

#### Mint From NEO

A user can also mint `cNEO` simply by transferring `NEO` to the `cNEO` contract with no parameters.

```
NEO.transfer(account, cNEO, quantity, null), where

NEO       is the the NeoToken contract
account   is the address that wishes to mint cNEO
cNEO      is the NeoCompounder contract
quantity  is the quantity of NEO to be converted into cNEO 
```

### Burn

#### Burn For bNEO

A user can burn `cNEO` to retrieve `bNEO` by transferring `cNEO` to the `cNEO` contract with no paramers.

```
cNEO.transfer(account, cNEO, quantity, null), where

cNEO      is the NeoCompounder contract
account   is the address that wishes to mint cNEO
quantity  is the quantity of cNEO to be converted into bNEO 
```

#### Burn For NEO *(not implemented)*

We have decided not to support burning `cNEO` directly for `NEO` in the `cNEO` contract.
The primary reason is that it is difficult to implement this while adhering to the paradigm of "burn `x cNEO` to receive `y bNEO`",
both because `NEO` is indivisible and because NeoBurger charges a fee of `0.001 GAS` per redemption of `bNEO`.
Applications can still support burning `cNEO` for `NEO` by using `invokeMulti` if they wish.

### Compound

A user can compound the underlying `bNEO` reserves of `cNEO` by calling `compound` with their wallet address.

```
cNEO.compound(account), where

cNEO      is the NeoCompounder contract
account   is the address of the transaction signer
```

Because the call to `compound` must contain the `cNEO` contract as a valid witness for the `GAS` transfer to the `bNEO-GAS` pool,
the `cNEO` contract must also be listed as a witness on the transaction.
To preclude the vulnerability in which an attacker repeatedly invokes FAULT transactions with the contract's `GAS` reserves,
the signers and witnesses must be ordered as `(account, cNEO)`.

---

## Events

  | Event Name | Arguments |
  | ---------------- | ----------- |
  | Mint             | `(account, mintQuantity)` |
  | Burn             | `(account, burnQuantity)` |
  | Transfer         | `(from, to, transferQuantity)` |
  | Compound         | `(account, gasQuantity, bneoQuantity, treasuryCut)` |
  | TopUpGas         | `(account, topUpQuantity)` |
  | WithdrawGas      | `(account, withdrawQuantity)` |
  | CompoundReserves | `(gasQuantity, bneoQuantity)` |
  | ConvertToNeo     | `(neoQuantity)` |
  | ConvertToBneo    | `(bneoQuantity)` |

---

## Design Considerations

### Simplicity

NeoCompounder is intentionally designed to have a very narrow set of features that are implemented cleanly and elegantly.
There are currently no plans to extend its feature set except to increase ease of use or protocol profitability.
If you have ideas for additional features, please feel free to fork and deploy a smarter `cNEO`.

### No Single Point of Failure

NeoCompounder is designed to be able to operate in perpetuity even if the contract owner wallet is forever lost for whatever reason.
Anyone can top up the `GAS` reserves of the contract if it is running a deficit, and anyone can call the `compound` method.
Furthermore, because the contract is fully open-source, anyone can deploy a new version, withdraw their funds from the contract, and continue operations there.

### Incentivized Compounding

In order to decentralize the operations as much as possible, `cNEO` allows anyone to call the `compound` method, provided that `compoundPeriod` has elapsed since the previous call.
The contract sends `GAS` back to the caller to make the call profitable.
`cNEO` funds these compounding calls by taking `feePercent` of the `GAS` claimed at every call of `compound` in its treasury.

### Compound Period

The `compoundPeriod` is adjustable and will be continuously tweaked to ensure that the amount of `GAS` swapped every period is not very large.
This is to ensure that it will not be profitable for an attacker to move the `bNEO-GAS` pool in anticipation of the `compound` call on the next block.
Initially, `compoundPeriod` will be set to `1 week`.
This can be set to `1 day` or even smaller depending on the eventual growth of `cNEO`.

### Voting

Although this will not be used in the beginning, `cNEO` has the ability to convert a portion of its `bNEO` reserves into `NEO` to vote directly.
This is to ensure that there is a way for NeoCompounder to strategize separately if it becomes more `GAS`-efficient to directly vote using a part of its reserves without a contract update.
