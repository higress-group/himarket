import type { ICliProvider } from "../../lib/apis/cliProvider";
import { CliSelector } from "../common/CliSelector";

interface HiCliSelectorProps {
  onSelect: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider) => void;
  disabled: boolean;
}

export function HiCliSelector({ onSelect, disabled }: HiCliSelectorProps) {
  return (
    <CliSelector
      onSelect={onSelect}
      disabled={disabled}
      showRuntimeSelector={true}
    />
  );
}
